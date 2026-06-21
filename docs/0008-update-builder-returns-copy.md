# ADR-0008: The UPDATE builder returns a new `T` via `copy()`, not a mutated receiver

**Status:** Accepted (builder shape amended by [ADR-0017](0017-drop-merge-strategy.md))
**Date:** 2026-06-21

> **Note.** The core decision here — the builder is `T.() -> T` and returns a `copy()` rather
> than mutating the receiver — still holds. The `strategy: MergeStrategy` parameter shown in the
> signatures below was later removed: the update builder is now just `set(builder: T.() -> T)`,
> always running over the current value, and `REPLACE`-via-builder was dropped as redundant with
> the `set(value)` overload. See ADR-0017.

## Context

T5.2 implements partial writes through a merge strategy:

```kotlin
user.set(MergeStrategy.UPDATE) {
    name = "Khuram M."
}
```

api-design §10 types the builder as `T.() -> Unit`, and §3 shows the body assigning a property
(`name = "Khuram M."`). These two cannot both hold for the library's canonical document type. A
`Unit`-returning builder must mutate its receiver in place, which requires `T` to expose mutable
state — but every documented `@Serializable` type is an immutable data class (the §2 `User` is all
`val`s), and field decomposition (ADR-0001/0003) is built around immutable, serializer-driven
types. The literal signature and the literal usage example contradict each other.

Making `T.() -> Unit` work would require one of:

- forcing users' domain types to expose `var` properties (a real constraint leaking into their
  models, and at odds with idiomatic `@Serializable` data classes), or
- generating or reflecting a mutable mirror type per document — substantial machinery,
  disproportionate to v1's scope and fragile across KMP targets.

## Decision

Change the builder signature to **`T.() -> T`**: the builder receives the current document value
as receiver and returns the new value, idiomatically via `copy()`.

```kotlin
public fun set(strategy: MergeStrategy, builder: T.() -> T)

user.set(MergeStrategy.UPDATE) {
    copy(name = "Khuram M.")
}
```

Semantics:

- `REPLACE` ignores any persisted state; the builder runs against the **defaults instance** and
  its result is written whole (equivalent to `set(builder(defaults))`).
- `UPDATE` runs the builder against the **current value**, or the **defaults instance** when the
  document is absent (test-plan §2), then writes the result with REPLACE semantics
  (clear-then-encode), so untouched fields keep their persisted values (test-plan §3) and no stale
  key survives.
- The "defaults instance" is obtained by decoding an empty document: the existing
  `DocumentDecoder` already returns declared field defaults for absent optional keys, so a type
  whose every field is optional decodes cleanly from empty storage. A type with required (non-
  optional, non-default) fields has no defaults to start from; `UPDATE`/`REPLACE`-via-builder on a
  missing such document surfaces the decoder's existing "Missing required field" failure. This is
  acceptable: there is no value the library could invent for a required field.

`copy()` is Kotlin's idiomatic partial-update mechanism and composes exactly with the merge
intent — touched fields change, untouched fields are carried over by `copy()` for free.

api-design §3 and §10 are corrected to `builder: T.() -> T` so the spec and code agree.

## Consequences

**Positive**
- Keeps `T` immutable; no constraint leaks into users' domain types.
- Zero codegen / reflection; reuses `decodeDocument` (for current/defaults) and the existing
  clear-then-encode `set` path unchanged.
- "Start from defaults on a missing document" falls out of the existing decoder behavior.

**Negative / cost**
- Deviates from the locked api-design's exact `-> Unit` type. Mitigated by this ADR and a one-line
  correction to api-design §3/§10 (the source of truth is updated, not silently diverged from).
- A document type with required fields cannot be UPDATE-ed from absent (no defaults to seed). This
  mirrors `get()` semantics and is the only honest behavior.

## Alternatives considered

- **Generated mutable mirror (`T.() -> Unit` literally honored)** — matches the doc example syntax
  but needs per-type codegen or reflection over decomposed keys; disproportionate for v1. Rejected.
- **Require users' types to have `var` properties** — leaks a mutability constraint into domain
  models and fights idiomatic `@Serializable` usage. Rejected.
- **Defer UPDATE to field delegates (Phase 7)** — per-field patching still needs a mutable surface
  and does not deliver T5.2's whole-document UPDATE now. Rejected.
