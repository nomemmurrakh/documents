# ADR-0017: Drop `MergeStrategy`; the overloads carry the intent

**Status:** Accepted
**Date:** 2026-06-21
**Amends:** ADR-0008 (builder shape)

## Context

Writing a document had two paths and three call shapes:

```kotlin
user.set(User(...))                         // replace whole object
user.set(MergeStrategy.UPDATE) { copy(...) } // build over current value
user.set(MergeStrategy.REPLACE) { copy(...) } // build over the type's defaults
```

The `MergeStrategy` enum (`REPLACE`, `UPDATE`) selected the baseline a builder started from. But
the two overloads already encode the intent without it:

- `set(value)` is given a complete object, so it is unambiguously a **replace**.
- `set { }` is given a builder over the current value, so it is unambiguously an **update**.

The strategy parameter was therefore redundant for the common case, and the one shape it uniquely
enabled — `set(REPLACE) { }`, building over defaults — is equivalent to constructing the value
fresh and calling `set(value)`. It existed mostly to leave room for future strategies (e.g. a deep
merge on the roadmap), i.e. speculative surface. This library has been trimming exactly that kind
of "room for later" aggressively (ADR-0015 removed the `Codec` seam; ADR-0016 removed forced
collections).

## Decision

Remove `MergeStrategy` from the public surface and collapse the builder overload:

```kotlin
public fun set(value: T)            // replace — a whole object is supplied
public fun set(builder: T.() -> T)  // update — runs over the current value (or defaults if absent)
```

- `set { }` always starts from the current value, falling back to the type's defaults when the
  document is absent (unchanged from the old `UPDATE` semantics).
- The builder is still `T.() -> T`, returning a `copy()` — ADR-0008 stands.
- `set(REPLACE) { }` (build over defaults) is dropped. It is expressible as `set(builder(defaults))`
  by the caller, and in practice as `set(SomeType(...))`.

When a genuinely different merge behavior is needed (e.g. `MERGE_DEEP`), it will get its own verb
or overload, designed against real requirements rather than reserved now.

## Consequences

**Positive**
- Smaller, more obvious public surface: the call site reads as its own intent, no enum to learn.
- One fewer public type (`MergeStrategy`) and one fewer parameter on the hot `set` path.
- `DocumentImpl.set(builder)` loses its `when (strategy)` branch — simpler implementation.

**Negative / cost**
- **Breaking API change** (pre-1.0): every `set(MergeStrategy.UPDATE) { }` call site becomes
  `set { }`, and `set(MergeStrategy.REPLACE) { }` must be rewritten as a whole-object `set(value)`.
  Acceptable before 1.0; recorded in the CHANGELOG.
- Loses the build-over-defaults-via-builder shape. Judged redundant with `set(value)`.

## Alternatives considered

- **Keep the enum with a default of `UPDATE`** — still exposes `MergeStrategy` and a second
  parameter for a distinction the overloads already make. Rejected.
- **Keep `REPLACE`-via-builder** — no call site needed it that `set(value)` couldn't express.
  Rejected as redundant surface.
