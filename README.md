# Documents

**Typed, reactive documents on top of MMKV — fast *and* a joy to write.**

A document-oriented Kotlin Multiplatform storage library backed by
[MMKV](https://github.com/Tencent/MMKV). Define a data class, treat it as a document, and get
typed reads, copy-style updates, and `Flow` reactivity — all riding on the fastest key-value
engine on mobile.

```kotlin
@Serializable
data class GameSave(val level: Int = 1, val coins: Int = 0, val unlockedBoss: Boolean = false)

val save = Documents.document<GameSave>("slot-1")   // one call, you have a document

save.set(GameSave(level = 1, coins = 0))
save.update { current -> current.copy(coins = current.coins + 50, level = current.level + 1) }
save.flow().collect { hud.render(it) }   // the HUD reacts to every write
```

That's the whole story. No schema, no DAO, no `MMKV.initialize`, no serialization plumbing.

---

## Why this exists

MMKV is *ridiculously* fast — memory-mapped, protobuf-backed, faster than just about anything
else on the platform. But it hands you a bare cupboard: raw keys, primitives only. Want to store
a typed object? Roll your own serialization and key management. Every. Single. Time.

The pleasant alternatives — typed, reactive, ergonomic — buy that comfort by giving the speed
back. So you're stuck picking a side: **fast**, or **nice to use**.

`Documents` refuses to pick. It drops an elegant, idiomatic Kotlin API — typed documents,
property delegates, a `copy()`-style update DSL, coroutines and `Flow` — straight onto MMKV. The
abstraction costs you a little CPU for serialization and **never a single extra byte of I/O**.
Clean code on top, MMKV all the way down.

Because libraries should be elegant — so the code people write with them can be elegant too.
That's the entire point of this one. ✨

## What to use it for

`Documents` is the persistence layer you reach for when you'd otherwise wire up
`SharedPreferences`, `NSUserDefaults`, Jetpack `DataStore`, raw `MMKV`, or a hand-rolled
key-value wrapper — one typed, reactive API that works the same on Android and iOS:

- **App settings & preferences** — theme, locale, feature flags, onboarding state. The classic
  `SharedPreferences` / `NSUserDefaults` job, but typed and observable.
- **Session & user state** — the signed-in user, auth tokens, the active profile. Put them in a
  named collection so logout is a single `clear`.
- **Caches & drafts** — last-synced payloads, an in-progress form, the "continue where you left
  off" blob. A separate collection keeps them apart from durable settings.
- **Reactive UI state** — anything Compose or SwiftUI should re-render when it changes: collect a
  `flow()` and the screen follows the store.
- **Shared KMP persistence** — one storage API in `commonMain`, so Android and iOS read and write
  the same documents through the same code.

It is **not** a relational database — if you need queries, joins, or large collections of rows,
reach for SQLite/Room/SQLDelight. `Documents` is for typed objects you address by key.

## How it feels

Open as many documents as you like with `Documents.document<T>(key)` — they all live in one
default MMKV file. Every document is quietly decomposed into **one storage key per top-level
field** (`{doc}::{field}`), so touching one field writes one key — nothing more. Values go through
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) into a compact internal
**CBOR** format. Zero per-type boilerplate, zero schema to babysit.

```kotlin
val save = Documents.document<GameSave>("slot-1")

save.set(GameSave(level = 5, coins = 120))   // write the whole document
val current: GameSave? = save.get()          // read it back (null if never written)

save.update { current ->                     // partial update, copy-style
    current.copy(coins = current.coins + 50) // bumps coins, leaves level untouched
}

save.update(GameSave::coins, 170)            // or write just that one key directly, no read
```

Need a separate file — a wipe-on-logout cache, per-user data, or an encrypted store? Open a named
**collection** and pull documents from it:

```kotlin
val cache = Documents.collection("cache")     // its own MMKV file
val draft = cache.document<Draft>("draft")
```

## Install

> 📦 `com.nomemmurrakh:documents` — on Maven Central.

```kotlin
// build.gradle.kts — a KMP module's commonMain, or an Android module
dependencies {
    implementation("com.nomemmurrakh:documents:0.1.0")
}
```

No setup ceremony: the library initializes MMKV for you, so you never touch `MMKV.initialize`.
Call `Documents.document(...)` and go.

## Quick start

```kotlin
import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import kotlinx.serialization.Serializable

@Serializable
data class Player(val name: String = "", val hp: Int = 100)

@Serializable
data class GameSave(
    val level: Int = 1,
    val coins: Int = 0,
    val player: Player = Player(),   // nested @Serializable — stored as one sub-blob
)

val save = Documents.document<GameSave>("slot-1")

save.set(GameSave(level = 3, coins = 75, player = Player("Mara", hp = 80)))
save.update { current -> current.copy(coins = current.coins + 50) }

println(save.get())     // GameSave(level=3, coins=125, player=Player(name=Mara, hp=80))
save.delete()
println(save.exists())  // false
```

## The API, end to end

### Open a document

```kotlin
// On the default store — the common case. The reified overload resolves the serializer for you.
val save = Documents.document<GameSave>("slot-1") {
    dispatcher = Dispatchers.Default  // dispatcher for flow/stateFlow collection (optional)
}
// ...or pass the serializer explicitly:
val save2 = Documents.document("slot-1", GameSave.serializer())
```

### Open a collection (a separate file)

Reach for a collection only when a set of documents needs its own MMKV file — a wipe-on-logout
cache, per-user data, or an encryption boundary:

```kotlin
val cache = Documents.collection("cache") {
    dispatcher = Dispatchers.Default  // dispatcher for flow/stateFlow collection
}
val draft = cache.document<Draft>("draft")

// An in-memory collection for tests — no MMKV, no persistence.
val test = Documents.inMemory()
val doc = test.document<GameSave>("slot-1")
```

**Single-process only.** Storage is always opened in MMKV's single-process mode: concurrent access
to the same store from more than one OS process (a background service, an app extension, etc.) is
not supported and can corrupt the store. If your app needs to share a store across processes,
this library isn't the right fit for that store today.

A document `key` can't contain the reserved separator `::` — try it and you'll get an
`IllegalArgumentException`.

### Read & write

```kotlin
save.get(): GameSave?                       // current value, or null if absent
save.set(value)                             // replace the whole document
save.update { current -> ... }              // update: build over the current value (or defaults)
save.update(GameSave::coins, 170)           // single-field update: writes just that key, no read
save.delete()                               // remove the document and all its field keys
save.exists(): Boolean                      // true if any field key is stored
```

Three call shapes carry the intent: `set(value)` **replaces** (a whole object is given),
`update { current -> ... }` **updates** (the builder runs over the current value), and
`update(prop, value)` **updates a single field directly**, with no read of the rest of the
document. The whole-object builder takes `current` as an explicit parameter — matching
`kotlinx.coroutines.flow.MutableStateFlow.update { current -> ... }` — and returns a new value via
`copy()`, starting from the persisted value, or the type's defaults when the document is absent,
leaving untouched fields exactly as they were. The whole read-modify-write runs under the
document's write lock, so multi-field updates are **atomic**. `update(prop, value)` skips that
read entirely — it's a single `putBytes` to one decomposed key, the same cost as a field-delegate
write (see "Bind a single field" below).

### React to changes

```kotlin
save.flow(): Flow<GameSave?>                  // cold; current value, then every committed write
save.stateFlow(scope): StateFlow<GameSave?>   // hot; shared while there are subscribers
```

`flow()` hands you the current value the moment you collect, then a fresh value after each
committed write — `null` when the document is deleted or absent. Emissions are conflated and fire
only once the write is durably committed. Writing a *different* document in the same collection
won't wake this one up.

Straight into Compose:

```kotlin
val save by saveDoc.flow().collectAsStateWithLifecycle(initialValue = saveDoc.get())
```

(`save` is nullable — `null` simply means "not written yet.")

### Bind a single field

Sometimes you don't want the whole document — just one field:

```kotlin
val coinsFlow: Flow<Int> = save.fieldFlow(GameSave::coins, default = 0)

var coins: Int by save.field(GameSave::coins, default = 0)
coins += 50       // writes only the coins field's key
println(coins)    // reads only that key
```

A field delegate reads and writes exactly one decomposed key. `fieldFlow` emits the current value
(or the default if never set), then a new value each time *that* field changes — a change to a
sibling field stays quiet.

For a one-off write with no delegate to declare, `save.update(GameSave::coins, 170)` does the same
single-key write directly — the non-delegate sibling to `field()`.

### When decoding fails

`get()` (and field reads) throw `DocumentDecodingException` if a stored field can't be decoded.
Since a document is one key per field, a single field can go bad while the rest stay healthy — so
the exception names the `documentKey`, the offending `field` when there is one, and wraps the
underlying cause. You get a clean library error instead of a bare `SerializationException`, so
your code never has to reach into the serialization layer's error types.

## Under the hood

- **Decomposition** — one MMKV entry per top-level field, keyed `{doc}::{field}`. Nested
  `@Serializable` types live as a single sub-blob under their field's key, to any depth. This is
  the trick that makes a one-field update cheap.
- **Serialization** — field values are encoded straight to bytes with an internal CBOR instance
  (`kotlinx-serialization-cbor`), no JSON text detour. It's an internal detail in v1, not a
  pluggable knob.
- **Storage SPI** — every bit of API and logic lives in `commonMain`. Only the `Storage`
  implementation is platform-specific: MMKV on Android and iOS, plus an in-memory one for tests.
- **Concurrency** — each document carries its own write lock (atomic builder updates), and
  reactive collection runs on the dispatcher you configure (`Dispatchers.Default` by default).

## Benchmarks

Documents decomposes a value into one `{doc}::{field}` key per field and runs a CBOR encode/decode
per field. These microbenchmarks measure that overhead against using raw MMKV directly — encoding
the same 5-field value once with CBOR and calling MMKV's byte API.

The numbers are captured on-device (Documents vs raw MMKV is not a JVM-host benchmark — MMKV is
mmap-backed and needs a real device/simulator). They are device- and OS-specific and are not run
in CI. See [ADR-0014](docs/adr/0014-on-device-benchmarks.md).

Sample type:

```kotlin
@Serializable
data class Profile(
    val id: Long,
    val name: String,
    val email: String,
    val age: Int,
    val active: Boolean,
)
```

### iOS

A `kotlin.time.TimeSource`-based timing harness (warmup + measured iterations; median / p95),
run on the simulator or a device:

```
./gradlew :documents:iosSimulatorArm64Test
```

Results print to test output as `BENCH <name> median=<n>ns p95=<n>ns`. Each raw-MMKV baseline does
the *same underlying work* as the Documents call it's paired with — the same field keys
(`"profile::id"`, `"profile::age"`, etc.), the same per-field CBOR encode/decode calls, the same
existence prefix-scan before a read, and the same prefix-scan-then-remove on clear — by hand, with
no library machinery (no lock, no change bus, no composite encoder). This isolates what the
abstraction costs on top of doing the identical raw operations manually, rather than comparing
Documents' per-field decomposition against a single whole-object blob (which would be a different,
unfair comparison).

| Operation                            | Documents | Raw MMKV (same work, by hand) |
| ------------------------------------- | --------- | ------------------------------ |
| `set(value)` (5 field)                | 22.3 µs   | 18.8 µs                        |
| `get` (5 field)                       | 20.2 µs   | 9.1 µs                         |
| `update { }` (whole-object, full get+set) | 44.2 µs   | 28.9 µs                        |
| `update(prop, value)` (1 field)       | 3.9 µs    | 2.3 µs                          |
| `delete` (incl. set, 5 field)         | 23.5 µs   | 18.8 µs                        |
| field delegate write (1 field)        | 5.2 µs    | 2.3 µs                         |

_Device: iPhone 17 Pro simulator · iOS 26.1 · median of 20k iterations, CBOR encoding. The
whole-object `update { }` builder always does a full 5-field get followed by a full 5-field set,
even when only one field changed — it's the read-modify-write path, and the raw baseline
replicates that same round trip rather than a hand-optimized single-key patch, so the pairing
stays honest. `update(prop, value)` is the true single-key write with no read — it comes in even
below field delegate write since it skips the `ReadWriteProperty` object the delegate allocates.
`delete` measures set + delete. Absolute timings are simulator (host CPU), not real hardware —
compare Documents vs raw MMKV within this table, not against other platforms._

### Android

Pending re-run. An instrumented harness already exists (`connectedAndroidDeviceTest`, same
`TimeSource` shape); the numbers here predate the CBOR switch and need a fresh on-device run.

## Platform support

| Platform | Status | Storage engine |
| -------- | :----: | -------------- |
| Android  | ✅ | MMKV |
| iOS — `arm64` + `simulatorArm64` | ✅ | MMKV (via CocoaPods) |

One public API across the board — it all lives in `commonMain`. MMKV is bound on Apple targets
through the Kotlin CocoaPods plugin, and the library owns MMKV initialization on both platforms,
so consumers never lift a finger.

## Try the sample

A runnable Android sample lives in [`sample/`](sample/) — a tiny Compose settings screen wired to
a single `Documents` document, with buttons that flip the theme and bump a launch counter, both
persisting instantly. Clone, run, tap. 👀

## License

See [LICENSE](LICENSE).
