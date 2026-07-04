# Documents — Product Requirements Document

**Status:** Draft v1.0
**Owner:** Nomem Labs
**Last updated:** 2026-06-15

---

## 1. Problem statement

Android and Kotlin Multiplatform apps frequently need to persist a *single record* of
structured data — a logged-in user, an app settings object, a session, a draft. The
existing options are all a poor fit for this shape:

- **SharedPreferences** — key-value only, no document model, awkward for nested objects,
  loads the whole file into memory, synchronous writes by default.
- **Jetpack DataStore** — async and typed, but Proto requires schema files and Preferences
  is still flat key-value. Heavy ceremony for "store one object."
- **SQLite / Room** — relational, table-oriented. Overkill for a single document; forces a
  schema and DAOs onto data that has no relations.
- **MMKV** — extremely fast (memory-mapped, protobuf-backed) but offers zero abstraction:
  raw keys and primitives only. Storing a typed object means hand-rolling serialization and
  key management every time.

There is no library that gives you a **document-oriented, typed, reactive API** with the
**performance of MMKV** underneath.

## 2. Target user

Kotlin / KMP developers (Android-first, multiplatform-aware) who:

- want to persist typed objects without writing a schema or a DAO,
- value an ergonomic, idiomatic Kotlin API (delegates, DSL, coroutines/Flow),
- don't want to pay SQLite's complexity tax or leave MMKV's performance on the table.

## 3. The gap (validated)

A prior ecosystem survey confirmed nothing combines all four of:
document-level delegate API + MMKV storage engine + field decomposition + kotlinx.serialization.
Existing libraries each cover at most two of these. `Documents` is the intersection.

## 4. Goals

- **G1** — A document-oriented public API: `get`, `set`, `set` with merge, `delete`, `exists`,
  `flow`, `stateFlow`, identical in shape to the original Documents design.
- **G2** — Typed persistence of any `@Serializable` object with no per-type boilerplate.
- **G3** — Reactivity: observers are notified when a document or field changes.
- **G4** — MMKV-class performance: the abstraction adds CPU/memory cost only, never extra I/O.
- **G5** — KMP-compatible public API (`commonMain`), with platform storage in platform source sets.
- **G6** — Pluggable serialization via a `Codec<T>` abstraction, defaulting to kotlinx.serialization.

## 5. Non-goals (v1)

Explicitly out of scope for the first release — do **not** build these:

- **NG1** — Querying, indexing, or collections. This is single-document storage, not a database.
- **NG2** — Encryption at rest (MMKV supports it; surface later, see roadmap).
- **NG3** — Schema migration blocks (`version` + `migrate {}`) — deferred to v2.
- **NG4** — Multi-process coordination guarantees beyond what MMKV provides.
- **NG5** — Cross-document transactions.
- **NG6** — Top-level `@JvmInline value class` document fields. The decomposition encoder/decoder
  reject them with a clear error; wrap the value in a regular field or `@Serializable` type
  instead. (Inline classes *nested inside* a `@Serializable` sub-blob field are fine.)

## 6. Success criteria

- A new user can persist and observe a typed object in under 10 lines, zero config.
- Read/write benchmarks within a small constant factor of raw MMKV (no I/O regression).
- Public API passes `explicitApi()` strict mode and binary-compatibility validation.
- Works unchanged in a KMP `commonMain` consumer with an Android target.
- 100% of public entry points documented with KDoc.

## 7. Open questions

- Final vocabulary: keep "document" as the user-facing noun, or `store` / `vault`?
  (Decision tracked in [ADR-0004](adr/0004-vocabulary.md).)
- Default dispatcher for suspend operations — `Dispatchers.IO` vs caller-provided?
