# ADR-0004: Keep "document" as the user-facing noun

**Status:** Accepted
**Date:** 2026-06-15

## Context

MMKV's mental model is a key-value *store*, not a document. The public API needs a single
consistent noun. Candidates: `document`, `store`, `vault`, `record`, `sheet`.

## Decision

Keep `document` as the user-facing noun and `Documents` as the library/root name. A document
is "one typed, serializable object persisted under a key."

## Consequences

**Positive**
- Matches the original Documents design the API is deliberately compatible with.
- Communicates the single-object (not collection, not table) intent clearly to users coming
  from Firestore-style mental models.

**Negative / cost**
- Slight conceptual mismatch with MMKV's underlying store model — internal docs clarify that
  "document" is the abstraction, not the storage primitive.

## Alternatives considered

- `store` / `vault` — accurate to MMKV but loses the document-oriented framing that is the
  product's whole pitch. Rejected.
