# Documents — API Design

**Status:** Draft v1.0
**Last updated:** 2026-06-15

> The API *is* the product. This document is the single source of truth for the public
> surface. Everything not described here is `internal` or `private`.

---

## 1. Creating a store

A `Documents` instance is the root handle, scoped to a named MMKV instance.

```kotlin
val store = Documents.create("app") {
    // optional configuration block; works with zero config
    json = Json { ignoreUnknownKeys = true } // default if omitted
    multiProcess = false                      // default
}
```

- `create(name)` with no block must work and use sensible defaults.
- `name` maps to the underlying MMKV instance id.
- MMKV is initialized automatically (Android via `androidx.startup`; iOS via `initializeMMKV`
  with the in-process sandbox path on first use). Consumers never call `MMKV.initialize` or pass a
  `Context` — see ADR-0012 (and ADR-0013 for the iOS CocoaPods binding).
- The default `Codec` is `KotlinxCodec`, constructed **per field type** from that field's
  `KSerializer` and the store's `json` (see ADR-0006). Custom codecs implement `Codec<T>`.

## 2. Declaring a document

A document is a typed handle to a single `@Serializable` object under a key.

```kotlin
@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val theme: Theme = Theme.SYSTEM,
)

val user: Document<User> = store.document("user")
```

`Document<T>` is the core user-facing type. `T` must be `@Serializable`.

## 3. Document operations

```kotlin
// Read — null if absent
val current: User? = user.get()

// Write — replaces the whole document
user.set(User(id = "1", name = "Khuram", email = "k@nomem.dev"))

// Partial write via merge strategy + builder
user.set(MergeStrategy.UPDATE) {
    // receiver is the current document; return the new value via copy() (see ADR-0008)
    copy(name = "Khuram M.")
}

// Existence
val present: Boolean = user.exists()

// Delete
user.delete()
```

### Merge strategies

```kotlin
enum class MergeStrategy {
    REPLACE, // overwrite all fields (default for set(value))
    UPDATE,  // apply only the fields touched in the builder, keep the rest
}
```

`set(strategy) { }` builds the new value from the existing one; the builder's receiver is the
current value and it returns the new value (idiomatically via `copy()`, see ADR-0008). On
`UPDATE`, untouched fields retain their persisted value. On a missing document, `UPDATE` starts
from defaults.

## 4. Reactivity

```kotlin
// Cold Flow — emits current value, then on every change. Emits null on delete.
val stream: Flow<User?> = user.flow()

// Hot StateFlow — requires a scope and an initial/derived value
val state: StateFlow<User?> = user.stateFlow(scope)
```

- `flow()` emits the current value on collection, then subsequent changes.
- Emission happens after the write is durably committed to MMKV.

## 5. Field delegates

Property-level ergonomics for settings-style usage.

```kotlin
class Settings(store: Documents) {
    private val doc = store.document<SettingsData>("settings")

    var theme: Theme by doc.field(SettingsData::theme, default = Theme.SYSTEM)
    val themeFlow: Flow<Theme> = doc.fieldFlow(SettingsData::theme, default = Theme.SYSTEM)
}
```

- `field(prop, default)` returns a `ReadWriteProperty` backed by a single decomposed key.
- Reading a never-set field returns `default`.
- Writing a field updates only that field's key and emits on the document's flow.
- `fieldFlow(prop, default)` emits the current field value (or `default` if never set) on
  collection, then the new value each time that field changes. A field's declared default is
  not recoverable from a `KProperty` at runtime, so the caller supplies it — see ADR-0010.

## 6. Custom serialization

```kotlin
interface Codec<T> {
    fun encode(value: T): ByteArray
    fun decode(bytes: ByteArray, deserializer: KSerializer<T>): T
}
```

Default: `KotlinxCodec`. Users may supply their own per-store or per-document codec.

## 7. Testability

```kotlin
// In-memory backend — no real MMKV instance needed in unit tests
val store = Documents.inMemory()
```

`inMemory()` returns a `Documents` with identical semantics backed by an in-memory map.

## 8. Threading & dispatchers

- Document operations (`get`/`set`/`delete`/`exists`, field delegates) are **synchronous and
  non-blocking** — MMKV is memory-mapped, so a read/write is a memory operation, not I/O.
- Writes to a document are serialized by a non-suspending reentrant lock, so a concurrent
  reader never observes a half-applied multi-field `UPDATE` (see ADR-0011).
- `flow()`/`stateFlow()` collection runs on a configurable dispatcher (default
  `Dispatchers.Default`, since the work is CPU-bound); `flow()` is safe to collect from any
  dispatcher and emissions are conflated.

## 9. Error contract

- `get()` returns `null` for absent documents — never throws for "missing".
- Deserialization failure throws `DocumentDecodingException` naming the document key,
  field, and underlying cause (never a bare `SerializationException`).
- Using a non-`@Serializable` `T` is a compile-time error where possible, otherwise a
  clear runtime `IllegalArgumentException` at `document()` time.

## 10. Full surface summary

```
Documents
  .create(name, block?): Documents
  .inMemory(): Documents
  .document<T>(key): Document<T>

Document<T>
  .get(): T?
  .set(value: T)
  .set(strategy: MergeStrategy, builder: T.() -> T)       // receiver = current; returns new (copy)
  .delete()
  .exists(): Boolean
  .flow(): Flow<T?>
  .stateFlow(scope): StateFlow<T?>
  .field(prop, default): ReadWriteProperty<Any?, V>
  .fieldFlow(prop, default): Flow<V>

MergeStrategy { REPLACE, UPDATE }
Codec<T> { encode, decode }
DocumentDecodingException
```
