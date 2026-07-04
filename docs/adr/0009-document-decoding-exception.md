# ADR-0009: Decoding failures surface as `DocumentDecodingException`, not a bare `SerializationException`

**Status:** Accepted
**Date:** 2026-06-21

## Context

T5.3 introduces the error path for reads. Because a document is decomposed into
per-field keys (`{doc}::{field}`), a read can fail mid-walk: a field's stored bytes
may be corrupt, truncated, or have been written by an incompatible schema. The
underlying call is `json.decodeFromString(...)` inside the field decoder, which throws
`kotlinx.serialization.SerializationException`.

A bare `SerializationException` is a poor contract for callers:

- It does not name **which document** failed.
- It does not name **which field** failed — and with decomposed storage, the offending
  field is exactly the actionable detail (one key may be corrupt while the rest are fine).
- It leaks the serialization layer as the library's public error surface.

api-design §9 and test-plan §7 both require: deserialization failure throws
`DocumentDecodingException` naming the document key, the field, and the underlying cause,
**never** a bare `SerializationException`.

There is a second, pre-existing throw in the decoder: the "Missing required field"
`IllegalStateException` raised when a required (non-optional) field's key is absent on a
document that otherwise exists. The question is whether that, too, should become a
`DocumentDecodingException`.

## Decision

Add a public exception:

```kotlin
public class DocumentDecodingException internal constructor(
    public val documentKey: String,
    public val field: String?,
    cause: Throwable?,
) : RuntimeException(<message naming key + field>, cause)
```

- `field` is nullable: a failure may be attributable to a specific field (the common case)
  or to the document as a whole (no single field implicated).
- The constructor is `internal` — only the library raises it; callers catch it.
- It extends `RuntimeException` (unchecked, matching Kotlin/kotlinx.serialization style and
  KMP, where there are no checked exceptions).

The field decoder wraps **both** failure modes in `DocumentDecodingException`:

1. A `SerializationException` (or any throwable) from `json.decodeFromString` for a field →
   `DocumentDecodingException(documentKey, field, cause)`.
2. The absent-required-field case → `DocumentDecodingException(documentKey, field, cause = null)`
   with a message stating the required field is missing. This replaces the prior
   `IllegalStateException` thrown in `read()`.

`DocumentImpl.get()` keeps its `exists()` guard, so a **fully absent** document still returns
`null` and never constructs this exception (api-design §9: "never throws for missing"). The
missing-required-field exception therefore only fires on a **partially present** document —
a genuine integrity failure, correctly classified as a decoding error.

`InMemoryStorage` and `defaults()`/`EmptyStorage` are unaffected: `defaults()` decodes an
all-absent baseline, which is only valid for all-optional types; a required-field type
decoding from `EmptyStorage` surfaces this same `DocumentDecodingException` (the documented
ADR-0008 edge case), now with the precise key/field rather than a bare `IllegalStateException`.

## Consequences

- The public surface gains `DocumentDecodingException` (already declared in api-design §10).
  ABI dump regenerated; `checkLegacyAbi` re-run.
- The decoder no longer throws `IllegalStateException` for a missing required field — tests or
  callers relying on that bare type must catch `DocumentDecodingException`. No such caller
  exists yet (T5.2's notes flagged the type as unspecified and asserted no arbitrary type).
- The `field` property lets callers branch on the offending field for diagnostics or repair.

## Alternatives considered

- **Leave `SerializationException` to propagate.** Rejected: violates §9/§7 and leaks the
  serialization layer.
- **Keep the missing-required-field `IllegalStateException` separate.** Rejected: from a
  caller's perspective a partially-present document that won't decode is a decoding failure;
  splitting it across two exception types for the same `get()` call complicates the contract.
- **Non-nullable `field`.** Rejected: not every decoding failure maps to a single field
  (e.g. a structure-level failure), and forcing a sentinel string is worse than `null`.
