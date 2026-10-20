# Documents — Architecture

**Status:** Draft v1.0
**Last updated:** 2026-10-20

> How the pieces fit. This is the decisions log that keeps the implementation from
> drifting. If a decision here is reversed, update this file and add an ADR.

---

## 1. Layered view

```
┌─────────────────────────────────────────────┐
│ Public API (commonMain)                       │
│   Documents, Document<T>, delegates, DSL       │
├─────────────────────────────────────────────┤
│ Decomposition layer (commonMain)              │
│   SerialDescriptor field-walker                │
│   key construction: {doc}::{field}             │
├─────────────────────────────────────────────┤
│ Serialization (commonMain)                    │
│   internal CBOR (kotlinx.serialization)        │
├─────────────────────────────────────────────┤
│ Reactivity (commonMain)                       │
│   MutableSharedFlow change bus                 │
├─────────────────────────────────────────────┤
│ Storage SPI (commonMain interface)            │
│   Storage { read/write/remove/contains/keys }  │
├──────────────────┬──────────────────────────┤
│ MmkvStorage      │ InMemoryStorage           │
│ (androidMain +   │ (commonMain, for tests)   │
│  native targets) │                            │
└──────────────────┴──────────────────────────┘
```

The public API and all logic live in `commonMain`. Only the concrete `Storage`
implementation is platform-specific, which keeps the KMP surface clean.

## 2. Storage SPI

A minimal interface so the engine is swappable (MMKV in prod, map in tests):

```kotlin
internal interface Storage {
    fun getBytes(key: String): ByteArray?
    fun putBytes(key: String, value: ByteArray)
    fun remove(key: String)
    fun contains(key: String): Boolean
    fun keys(prefix: String): List<String>
}
```

`MmkvStorage` wraps a Tencent MMKV instance. `InMemoryStorage` wraps a
`MutableMap<String, ByteArray>` guarded for concurrent access.

## 3. Field decomposition

Each document is **not** stored as one serialized blob. Instead every top-level field
becomes its own MMKV key:

```
user::id      -> bytes
user::name    -> bytes
user::email   -> bytes
user::theme   -> bytes
```

Key format: `{documentKey}::{fieldName}`. The `::` separator is reserved and rejected
in document keys.

**Why decompose** (see [ADR-0001](adr/0001-field-decomposition.md)): partial updates touch only changed keys, field-level
flows are possible, and we avoid read-modify-write of the whole object on every field set.

## 4. Walking fields with SerialDescriptor

We need each field's name, index, and serializer **without reflection** (for KMP/native).
kotlinx.serialization's `SerialDescriptor` provides exactly this:

- `descriptor.elementsCount` — number of fields.
- `descriptor.getElementName(i)` — field name → key suffix.
- `descriptor.getElementDescriptor(i)` — field's own descriptor / kind.

Write path: a custom `CompositeEncoder` writes each element to `{doc}::{name}` instead of
to a single output buffer. Read path: a custom `CompositeDecoder` reads each requested
element from its key, returning `CompositeDecoder.DECODE_DONE` when fields are exhausted and
handling absent keys (defaults / nullable) gracefully.

This is the one genuinely unfamiliar piece — budget design time for the encoder/decoder.

## 5. Serialization

A single internal CBOR format sits between the field-walker and `Storage`, serializing each
*field value* (not the whole object) directly to bytes (`encodeToByteArray`/
`decodeFromByteArray` — no text intermediate). A field whose type is itself a `@Serializable`
object is stored as a serialized sub-blob under its single field key in v1 (nested
decomposition is a possible later optimization, not a v1 goal). The format is internal and not
a public extension point — see [ADR-0015](adr/0015-cbor-internal-format.md) (which supersedes the earlier `Codec<T>` abstraction).

## 6. Reactivity

MMKV has no native change listeners, so reactivity is a process-local concern:

- A `MutableSharedFlow<String>` (replay 0, extraBuffer) acts as a change bus,
  emitting the affected `documentKey` after every committed write/delete. There is one bus
  per store, shared by every instance opened on that store (see
  [ADR-0022](adr/0022-process-wide-store-runtime.md)), so separate instances of the same
  document observe each other's writes.
- `document.flow()` filters the bus for its key, re-reads, and emits the new value
  (conflated). Initial value is emitted on collection.
- `fieldFlow(prop)` filters further by field.

Consequence: cross-process change notification is **not** provided in v1 (NG4). Documented
explicitly.

## 7. Source-set layout

```
documents/
  src/
    commonMain/   # all API + logic + Storage interface + InMemoryStorage
    androidMain/  # MmkvStorage (Android)
    iosMain/      # MmkvStorage (native), MMKV bound via CocoaPods — see ADR-0013
    commonTest/   # semantics tests against InMemoryStorage
    androidTest/  # MMKV-backed integration tests
    iosTest/      # MMKV-backed tests run on the iOS simulator
```

## 8. Performance stance

The abstraction adds only CPU/memory cost (descriptor walking, flow plumbing); it adds
**no extra I/O** — writes hit MMKV's memory-mapped file exactly as raw MMKV would. Field
decomposition can mean more, smaller writes than a single blob; this is acceptable and
benchmarked, not assumed.

## 9. Concurrency

- Writes are serialized per document to keep multi-field `update { }` atomic from the
  library's perspective. The lock is chosen by hashing the document key into 64 lazily created
  striped locks per store, so every instance of one document shares a lock while unrelated
  documents rarely contend. See [ADR-0022](adr/0022-process-wide-store-runtime.md).
- The change bus emits only after all field writes for an operation commit.

## 10. Process-wide store runtime

Coordination state lives with the store, not with the objects that open it
([ADR-0022](adr/0022-process-wide-store-runtime.md)):

- An internal `Runtime` object holds one `StoreRuntime` per store id for the lifetime of the
  process. A `StoreRuntime` owns the storage handle, the change bus, and the striped write
  locks for that store.
- `Documents.document` and `Documents.collection` resolve their store through `Runtime`, so
  opening the same store or document twice never splits the bus or the locks.
- `Runtime` initializes lazily on first access, after `ensureInitialized()`. Nothing runs at
  app startup and there is no shutdown.
- `Documents.inMemory()` builds a fresh, unregistered `StoreRuntime` per call, keeping tests
  isolated.
- Storage consistency is unaffected: MMKV already returns the same underlying store for the
  same id. Only in-process notification and locking are shared by the runtime.
