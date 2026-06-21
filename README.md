# Documents

A document-oriented, typed, reactive Kotlin Multiplatform storage library backed by
[MMKV](https://github.com/Tencent/MMKV).

## Why

MMKV is exceptionally fast — memory-mapped, protobuf-backed, faster than just about anything
else on the platform. But it gives you nothing to write *against*: raw keys, primitives only.
Persisting a typed object means hand-rolling serialization and key management every single time.

The more elegant alternatives — typed, reactive, ergonomic — give up that performance to get
there. So you're forced to choose: fast, or pleasant to use.

`Documents` refuses the trade. It puts an elegant, idiomatic Kotlin API — typed documents,
property delegates, a copy-style update DSL, coroutines and `Flow` — directly on top of MMKV,
adding only CPU cost for serialization, never extra I/O. You write code that reads cleanly, and
it still runs on MMKV underneath.

Libraries should be elegant, so that the code people write with them can be elegant too. That is
the whole point of this one.

## What it is

You define a plain `@Serializable` data class and treat it as a document:

```kotlin
@Serializable
data class Settings(
    val theme: String = "system",
    val launchCount: Int = 0,
)

val store = Documents.create("app")
val settings = store.document<Settings>("settings")

settings.set(Settings(theme = "dark"))          // write the whole document
val current: Settings? = settings.get()         // read it back (null if never written)

settings.set(MergeStrategy.UPDATE) {            // copy-style partial update
    copy(launchCount = launchCount + 1)
}
```

Each document is decomposed into **one storage key per top-level field** (`{doc}::{field}`), so a
single-field update touches only that field's key. Values are serialized with
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) to a compact internal
**CBOR** format — there is no per-type boilerplate and no schema to maintain.

## Installation

> Coordinates: `com.nomemmurrakh:documents`. Published to Maven Central.

```kotlin
// build.gradle.kts (a KMP module's commonMain, or an Android module)
dependencies {
    implementation("com.nomemmurrakh:documents:0.1.0")
}
```

The library initializes MMKV for you — you never call `MMKV.initialize`. Just call
`Documents.create(...)` and start using it.

## Quick start

```kotlin
import io.github.nomemmurrakh.documents.Documents
import io.github.nomemmurrakh.documents.MergeStrategy
import io.github.nomemmurrakh.documents.document
import kotlinx.serialization.Serializable

@Serializable
data class Profile(val name: String = "", val age: Int = 0)

val store = Documents.create("profiles")
val profile = store.document<Profile>("me")

profile.set(Profile(name = "Sam", age = 30))
profile.set(MergeStrategy.UPDATE) { copy(age = 31) }

println(profile.get())   // Profile(name=Sam, age=31)
profile.delete()
println(profile.exists()) // false
```

## API

### Opening a store and documents

```kotlin
// Persistent store, backed by a named MMKV instance.
val store = Documents.create("app") {
    multiProcess = false              // share across processes (default false)
    dispatcher = Dispatchers.Default  // dispatcher for flow/stateFlow collection
}

// In-memory store, for tests. No MMKV, no persistence.
val test = Documents.inMemory()

// Open a typed document. The reified overload resolves the serializer for you.
val doc = store.document<Profile>("me")
// or, explicit serializer:
val doc2 = store.document("me", Profile.serializer())
```

A document `key` must not contain the reserved separator `::` — doing so throws
`IllegalArgumentException`.

### Reading and writing

```kotlin
doc.get(): Profile?                       // current value, or null if absent
doc.set(value)                            // replace the whole document
doc.set(MergeStrategy.UPDATE) { ... }     // builder over the current value (or defaults)
doc.set(MergeStrategy.REPLACE) { ... }    // builder over the type's defaults
doc.delete()                              // remove the document and all its field keys
doc.exists(): Boolean                     // true if at least one field key is stored
```

`set(strategy) { ... }` is a `T.() -> T` builder — return a `copy()`, not a mutated receiver.
The read-modify-write runs under the document's write lock, so a multi-field update is atomic.

- **`UPDATE`** starts from the persisted value (or the type's defaults if absent), leaving
  untouched fields intact.
- **`REPLACE`** starts from the type's defaults, ignoring whatever is persisted.

### Reactivity

```kotlin
doc.flow(): Flow<Profile?>                       // cold; current value, then each committed write
doc.stateFlow(scope): StateFlow<Profile?>        // hot; shared while subscribed
```

`flow()` emits the current value on collection, then the new value after every committed write to
this document. It emits `null` when the document is deleted or absent. Emissions are conflated and
only happen once the write is durably committed. A write to a *different* document on the same
store does not notify this one.

In Compose:

```kotlin
val settings by doc.flow().collectAsStateWithLifecycle(initialValue = doc.get())
```

(`settings` is nullable — `null` means the document has not been written yet.)

### Field delegates

When you want to bind a single field rather than the whole document:

```kotlin
val themeFlow: Flow<String> = doc.fieldFlow(Profile::name, default = "")

var name: String by doc.field(Profile::name, default = "")
name = "Sam"          // writes only the name field's key
println(name)         // reads only that key
```

A field delegate reads and writes exactly one decomposed key. `fieldFlow` emits the current value
(or the default if never set) on collection, then the new value each time *that* field changes; a
change to another field of the same document does not emit.

### Errors

`get()` (and field reads) throw `DocumentDecodingException` when a stored field cannot be decoded.
Because a document is one key per field, a read can fail on a single field while the rest are
intact — the exception names the `documentKey` and, when one field is implicated, the `field`, and
wraps the underlying cause. It is raised instead of a bare `SerializationException` so callers
never depend on the serialization layer's error types.

## How it works

- **Decomposition.** A document is stored as one MMKV entry per top-level field, keyed
  `{doc}::{field}`. Nested `@Serializable` types are stored as a single sub-blob under their
  field's key; nesting depth is unrestricted. This is what makes a one-field update cheap.
- **Serialization.** Field values are encoded directly to bytes with an internal CBOR instance
  (`kotlinx-serialization-cbor`) — no JSON text hop. Serialization is an internal detail in v1,
  not a configurable extension point.
- **Storage SPI.** All API and logic live in `commonMain`. Only the `Storage` implementation is
  platform-specific: MMKV on both Android and iOS, plus an in-memory implementation for tests.
- **Concurrency.** Each document has its own write lock, so builder-style updates are atomic;
  reactive collection runs on the configured dispatcher (`Dispatchers.Default` by default).

## Platform support

| Platform | Status | Storage engine |
| -------- | ------ | -------------- |
| Android  | ✅      | MMKV           |
| iOS (arm64 + simulator arm64) | ✅ | MMKV (via CocoaPods) |

The public API is identical across platforms — it lives entirely in `commonMain`. MMKV is bound
on Apple targets through the Kotlin CocoaPods plugin. The library owns MMKV initialization on both
platforms, so consumers never initialize it themselves.

## Sample

A runnable Android sample lives in [`sample/`](sample/) — a small Compose settings screen built on
a single `Documents` document.

## License

See [LICENSE](LICENSE).
