# ADR-0007: The `Documents` root factory is built alongside T5.1

**Status:** Accepted
**Date:** 2026-06-21

## Context

`docs/tasks.md` has no task for the `Documents` root handle, yet api-design §1, §7, and §10
specify it as the only documented way to obtain a `Document<T>`:

```kotlin
Documents
  .create(name, block?): Documents
  .inMemory(): Documents
  .document<T>(key): Document<T>
```

Two facts force the issue before T5.1 (`Document<T>`) can be tested:

- A `Document<T>` is unreachable without a factory that produces it. The decomposition layer
  (T4.x) is wired through internal `encodeDocument` / `decodeDocument` free functions taking an
  explicit `Storage` and `Json`; nothing public hands a user a `Document<T>`.
- The test-plan assumes `Documents.inMemory()` as the entry point for the `commonTest` suite
  ("Most run in `commonTest` against `InMemoryStorage`"). T5.1's tests cannot be written from the
  test-plan without it.

`create(name)` binds a real MMKV instance, which only exists in `androidMain`. The common surface
therefore cannot construct MMKV directly; only `inMemory()` is fully constructible in `commonMain`.

## Decision

Build the `Documents` root **alongside T5.1** rather than adding a separate task. Specifically:

- `Documents` is a **public interface** in `commonMain` exposing `document<reified T>(key)`,
  with the reified overload capturing `serializer<T>()` and delegating to a non-reified internal
  member. Its constructor surface is the companion factories, not a public constructor.
- `Documents.inMemory()` lives in `commonMain` and backs the handle with `InMemoryStorage`.
- `Documents.create(name) { }` is declared on the companion but its MMKV-backed `Storage` is
  supplied from `androidMain` via an internal `expect/actual` storage factory — the common
  factory body stays platform-agnostic. (Deferred to when MMKV wiring lands; `inMemory()` is
  sufficient for T5.1 tests.)
- The internal implementation holds `(storage, json)` and constructs each `Document<T>` with
  `(key, KSerializer<T>, storage, json)`, reusing the existing `encodeDocument` /
  `decodeDocument` functions unchanged.

`document()` validates the key eagerly via `Keys.prefix(key)`, surfacing the `::` rejection
(api-design §9, test-plan §7) at `document()` time rather than first write.

## Consequences

**Positive**
- Unblocks T5.1 with a test-plan-faithful entry point (`inMemory()`).
- Keeps all API and logic in `commonMain`; only the MMKV `Storage` is platform-specific, per the
  architecture's layering rule.
- No change to the locked public surface — this *implements* api-design §1/§7/§10, it does not
  extend it.

**Negative / cost**
- `tasks.md` understated the work; T5.1 now also delivers the root handle. Recorded here and
  reflected by a note in `tasks.md`.

## Alternatives considered

- **A dedicated task before T5.1** — cleaner bookkeeping, but the root handle has almost no
  behavior of its own (it is a factory); testing it in isolation would duplicate T5.1's tests.
  Rejected in favor of co-delivery.
- **Expose `Document` construction directly without a `Documents` root** — contradicts
  api-design §1/§10 and the test-plan's assumed entry point. Rejected.
