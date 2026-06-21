# Documents — Roadmap

**Last updated:** 2026-06-15

Versioning follows SemVer. Breaking changes only in major releases; deprecation cycles
(warning → error → hidden) across minors, per the Kotlin backward-compatibility guidelines.

---

## v1.0 — Core (ship first)

The minimum that delivers the product thesis.

- `Documents.create` + zero-config defaults
- `Document<T>`: `get`, `set`, `set(UPDATE) {}`, `delete`, `exists`
- Field decomposition + `SerialDescriptor` encoder/decoder
- `Codec<T>` with `KotlinxCodec` default
- `flow()` / `stateFlow()` via the SharedFlow change bus
- Field delegates: `field(prop, default)`, `fieldFlow(prop)`
- `InMemoryStorage` + `Documents.inMemory()` for tests
- Android `MmkvStorage`
- iOS `MmkvStorage` (MMKV bound via the Kotlin CocoaPods plugin — see ADR-0013)
- `explicitApi()` strict, binary-compatibility validator, full KDoc, README, sample

## v1.x — Hardening

- Benchmarks vs raw MMKV / DataStore / SharedPreferences published in README
- More merge strategies if demand appears (e.g. `MERGE_DEEP`)

## v2.0 — Migrations & security

- `version` + `migrate { }` blocks for renamed/retyped fields (currently NG3)
- Encryption at rest, surfacing MMKV's crypt key support (currently NG2)
- Opt-in (`@RequiresOptIn`) markers for any experimental surface introduced

## Later / maybe

- Recursive decomposition of nested `@Serializable` fields
- KSP processor to warn on non-`@Serializable` types at compile time
- Multi-process change notification (currently NG4)
