# ADR-0001: Decompose documents into per-field keys

**Status:** Accepted
**Date:** 2026-06-15

## Context

A document of type `T` could be persisted either as a single serialized blob under one MMKV
key, or decomposed into one key per top-level field (`{doc}::{field}`).

## Decision

Decompose into per-field keys.

## Consequences

**Positive**
- Partial updates (`set(MergeStrategy.UPDATE)`) write only the changed fields instead of
  re-serializing and re-writing the entire object.
- Enables field-level reactivity (`fieldFlow`) and field delegates backed by a single key.
- Avoids whole-object read-modify-write on every small change.

**Negative / cost**
- More keys to manage; reads of the full object fan out across N keys.
- Requires a custom `CompositeEncoder`/`CompositeDecoder` walking `SerialDescriptor`
  (the main implementation complexity).
- Nested `@Serializable` fields are stored as sub-blobs in v1 rather than recursively
  decomposed.

## Alternatives considered

- **Single blob per document** — simpler to implement, but every field write rewrites the
  whole object and field-level flows become impossible. Rejected; it defeats the library's
  reason to exist over raw MMKV.
