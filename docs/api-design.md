# Documents — API Design

**Status:** Draft v1.0
**Last updated:** 2026-06-15

> The API *is* the product. This document is the single source of truth for the public
> surface. Everything not described here is `internal` or `private`.

---

## 1. Opening documents

`Documents` is the entry point. Open a document directly on the default store, or open a named
`Collection` when a set of documents needs its own MMKV file. See [ADR-0016](adr/0016-documents-entry-point-and-collections.md).

```kotlin
// Default store — the common case.
val doc = Documents.document<User>("user") {
    // optional configuration block; works with zero config
    dispatcher = Dispatchers.Default // default
}

// A separate MMKV file, for a distinct lifecycle/access pattern.
val cache = Documents.collection("cache") {
    multiProcess = false // default
}
val draft = cache.document<Draft>("draft")
```

- `document<T>(key)` with no block must work and use sensible defaults; it opens (get-or-open,
  not "create anew") the document under `key` in the default store.
- `collection(name)` maps `name` to the underlying MMKV instance id; open a collection only for a
  wipe-on-logout cache, per-user scoping, multi-process sharing, or an encryption boundary.
- The default store's config exposes `dispatcher` only; a collection's config exposes
  `multiProcess` and `dispatcher`.
- MMKV is initialized automatically (Android via `androidx.startup`; iOS via `initializeMMKV`
  with the in-process sandbox path on first use). Consumers never call `MMKV.initialize` or pass a
  `Context` — see [ADR-0012](adr/0012-automatic-mmkv-initialization.md) (and [ADR-0013](adr/0013-ios-mmkv-via-cocoapods.md) for the iOS CocoaPods binding).
- Field values are serialized with a single internal CBOR format (see [ADR-0015](adr/0015-cbor-internal-format.md)); the on-disk
  format is not configurable and is not a public extension point in v1.

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

// Whole-object update via builder
user.update { current ->
    current.copy(name = "Khuram M.")
}

// Single-field update — writes only that field's key, no read-modify-write
user.update(User::name, "Khuram M.")

// Existence
val present: Boolean = user.exists()

// Delete
user.delete()
```

### Update vs. replace vs. single-field update

Three call shapes, three intents; no strategy enum (see [ADR-0017](adr/0017-drop-merge-strategy.md), extended by [ADR-0018](adr/0018-update-verb-and-single-field-update.md)).

- `set(value)` **replaces** the whole document — a complete object is supplied.
- `update { current -> ... }` **updates** it — the builder receives the current value (or the
  type's defaults when the document is absent) as an explicit parameter and returns the new
  value, idiomatically via `copy()` ([ADR-0008](adr/0008-update-builder-returns-copy.md), [ADR-0018](adr/0018-update-verb-and-single-field-update.md)). Untouched fields retain their persisted
  value. This is a read-modify-write under the document's write lock.
- `update(prop, value)` **updates a single field directly** — no read, no decode of the rest of
  the document; it writes exactly one decomposed key ([ADR-0018](adr/0018-update-verb-and-single-field-update.md)).

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
class Settings {
    private val doc = Documents.document<SettingsData>("settings")

    var theme: Theme by doc.field(SettingsData::theme, default = Theme.SYSTEM)
    val themeFlow: Flow<Theme> = doc.fieldFlow(SettingsData::theme, default = Theme.SYSTEM)
}
```

- `field(prop, default)` returns a `ReadWriteProperty` backed by a single decomposed key.
- Reading a never-set field returns `default`.
- Writing a field updates only that field's key and emits on the document's flow.
- `fieldFlow(prop, default)` emits the current field value (or `default` if never set) on
  collection, then the new value each time that field changes. A field's declared default is
  not recoverable from a `KProperty` at runtime, so the caller supplies it — see [ADR-0010](adr/0010-field-delegate-serializer.md).
- `update(prop, value)` (§3) is a related but distinct single-field write: it is a plain function
  call with no `ReadWriteProperty`/`by` involved, for one-off field writes outside a class that
  owns a `var`-backed delegate.

## 6. Serialization

Serialization is internal. Each field value is encoded to bytes with a single internal CBOR
format and stored under its decomposed key. The on-disk format is not configurable and is not
a public extension point in v1 — see [ADR-0015](adr/0015-cbor-internal-format.md).

## 7. Testability

```kotlin
// In-memory backend — no real MMKV instance needed in unit tests
val store = Documents.inMemory()
val doc = store.document<SettingsData>("settings")
```

`inMemory()` returns a `Collection` with identical semantics backed by an in-memory map.

## 8. Threading & dispatchers

- Document operations (`get`/`set`/`delete`/`exists`, field delegates) are **synchronous and
  non-blocking** — MMKV is memory-mapped, so a read/write is a memory operation, not I/O.
- Writes to a document are serialized by a non-suspending reentrant lock, so a concurrent
  reader never observes a half-applied multi-field `UPDATE` (see [ADR-0011](adr/0011-synchronous-api-nonsuspend-lock.md)).
- `flow()`/`stateFlow()` collection runs on a configurable dispatcher (default
  `Dispatchers.Default`, since the work is CPU-bound); `flow()` is safe to collect from any
  dispatcher and emissions are conflated.

## 9. Error contract

- `get()` returns `null` for absent documents — never throws for "missing".
- Deserialization failure throws `DocumentDecodingException` naming the document key,
  field, and underlying cause (never a bare low-level decode exception — a corrupt CBOR field
  may surface from the format as `SerializationException`, `IllegalStateException`, or
  `IllegalArgumentException`; all are wrapped).
- Using a non-`@Serializable` `T` is a compile-time error where possible, otherwise a
  clear runtime `IllegalArgumentException` at `document()` time.

## 10. Full surface summary

```
Documents
  .document<T>(key, block?): Document<T>      // default store; block configures dispatcher
  .collection(name, block?): Collection       // named MMKV file; block configures multiProcess, dispatcher
  .inMemory(): Collection

Collection
  .document<T>(key): Document<T>

Document<T>
  .get(): T?
  .set(value: T)                                          // replace whole document
  .update(builder: (T) -> T)                              // update; builder takes current, returns new (copy)
  .update(prop: KProperty1<T, V>, value: V)                // single-field write, no read (extension fn)
  .delete()
  .exists(): Boolean
  .flow(): Flow<T?>
  .stateFlow(scope): StateFlow<T?>
  .field(prop, default): ReadWriteProperty<Any?, V>
  .fieldFlow(prop, default): Flow<V>

DocumentDecodingException
```
