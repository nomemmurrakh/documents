# Documents

A document-oriented, typed, reactive Kotlin Multiplatform storage library — the ergonomics
of Firestore-style documents with the performance of [MMKV](https://github.com/Tencent/MMKV)
underneath.

> Persist one typed object. No schema, no DAO, no boilerplate.

```kotlin
@Serializable
data class User(val id: String, val name: String, val theme: Theme = Theme.SYSTEM)

val user = Documents.document<User>("user")

user.set(User(id = "1", name = "Khuram"))
user.set { copy(name = "Khuram M.") }

user.flow().collect { println(it) }   // reactive, emits on every change
val current: User? = user.get()
```

## Why

`SharedPreferences` is flat key-value. `SQLite`/`Room` is a relational sledgehammer for one
record. `MMKV` is blazing fast but gives you no abstraction. `Documents` is the missing
middle: a typed, document-shaped, reactive API on top of MMKV's memory-mapped speed.

## Features

- **Typed documents** — persist any `@Serializable` object, zero per-type boilerplate.
- **Reactive** — `flow()`, `stateFlow()`, and field-level `fieldFlow()`.
- **Partial updates** — `set { }` writes only changed fields.
- **Field delegates** — `var theme by doc.field(SettingsData::theme, default = …)`.
- **Fast** — field decomposition over MMKV; the abstraction adds no extra I/O.
- **Multiplatform** — public API in `commonMain`; storage is the only platform piece.
- **Testable** — `Documents.inMemory()` for unit tests, no real MMKV needed.

## Install

> Published to Maven Central (the library has Apple targets, which JitPack can't build —
> see [ADR-0005](docs/0005-publishing-maven-central.md)).

```kotlin
repositories { mavenCentral() }

dependencies {
    implementation("com.nomemmurrakh:documents:<version>")
}
```

In a KMP consumer, add it to `commonMain` — each platform resolves its own klib automatically.

## Documentation

- [API design](docs/api-design.md)
- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Contributing](CONTRIBUTING.md)

## Status

Pre-1.0, under active development. The public API is being stabilized against
[`docs/api-design.md`](docs/api-design.md).

## License

Apache 2.0 — see [LICENSE](LICENSE).
