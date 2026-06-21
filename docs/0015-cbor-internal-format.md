# ADR-0015: Single internal CBOR format; remove the Codec abstraction

**Status:** Accepted
**Date:** 2026-06-21
**Supersedes:** ADR-0006

## Context

The codec layer was the only place a serialization format appeared. Two problems surfaced
while reviewing the on-disk format before the v0.1.0 tag (the format is a data decision —
changing it after a release is a migration, so it must be settled pre-tag):

1. **Double conversion.** The format was JSON, a `StringFormat`. Every field was converted
   twice on write — typed value → JSON *text* → UTF-8 *bytes* — and twice on read. The text
   hop is pure overhead for a byte-oriented mmap store and leaves the on-disk bytes as UTF-8
   text rather than clean binary.

2. **A dead abstraction.** The public `Codec<T>` interface (api-design §6) and its default
   `KotlinxCodec<T>` (ADR-0006) were intended as the format-pluggability seam, but nothing in
   the production path ever used them. The real format was a concrete
   `kotlinx.serialization.json.Json` field threaded through `DocumentImpl`, the
   `DocumentEncoder`/`DocumentDecoder`, and `decodeDocument`/`encodeDocument`, plus a public
   `DocumentsConfig.json` knob. `Codec`/`KotlinxCodec` were public surface that looked
   load-bearing but were exercised only by their own test.

## Decision

1. **Use CBOR as the single on-disk format.** Replace `Json` with one internal
   `Cbor { ignoreUnknownKeys = true }` instance throughout the document path. Encode/decode go
   straight to/from bytes (`encodeToByteArray`/`decodeFromByteArray`); the intermediate string
   conversions are removed.

2. **Remove the format extension point.** Delete the public `Codec<T>` interface, the
   `KotlinxCodec<T>` class, and the public `DocumentsConfig.json` property. There is no
   per-store/per-document format knob in v1: one library, one well-chosen format. A genuinely
   pluggable format would touch the whole encoder/decoder and can be designed properly later if
   demand appears; shipping a vestigial per-field codec interface is not that.

3. **Drop the `"null"` text sentinel for native CBOR null.** The encoder previously wrote the
   literal text `"null"` for a nullable-null field and the decoder string-matched it. CBOR has
   a native null (`0xF6`), so nullable fields are encoded through their nullable serializer and
   the sentinel is gone. The decoder short-circuits a lone `0xF6` byte to `null` (the composite
   decoder may be handed the non-null element deserializer, which cannot itself read CBOR null).
   Absent *key* still means null/default (unchanged, key-presence based).

4. **Wrap all low-level CBOR decode failures in `DocumentDecodingException`.** JSON surfaced
   malformed input as `SerializationException`; CBOR can also throw `IllegalStateException`
   (e.g. truncated input — "Unexpected EOF") or `IllegalArgumentException`. The decode path
   catches all three and wraps them, preserving the api-design §9 contract that a corrupt field
   never leaks a bare low-level exception.

CBOR is chosen over ProtoBuf because it is self-describing and tolerant of schema evolution
(`ignoreUnknownKeys`), matching JSON's flexibility for an evolving document store; ProtoBuf
needs stable field numbers and is stricter about change.

## Consequences

**Positive**
- One conversion (typed ↔ bytes); no UTF-8 text detour; clean binary on disk.
- Binary blobs are the natural input to a future encryption layer.
- Smaller public surface: `Codec`, `KotlinxCodec`, and `DocumentsConfig.json` leave the ABI
  (a pure shrink).
- Honest design — no abstraction that pretends to be an extension point it never was.

**Negative / cost**
- **Breaking on-disk format change.** CBOR bytes are not JSON; any data written by a prior
  JSON build will not decode after upgrade. Acceptable at 0.1.0 / pre-1.0; no migration is
  provided (out of scope). Recorded in the changelog.
- The on-disk bytes are no longer human-readable. Tooling that wants to inspect a store reads
  bytes → typed value → renders as JSON at read time; readability moves to the tool layer,
  where it belongs.
- Format is no longer configurable by consumers (intended — see Decision 2).

## Alternatives considered

- **Keep JSON.** Human-readable on disk, no new dependency, but keeps the double conversion and
  leaves `Codec` as dead public surface. Rejected — the format decision is cheapest to make now.
- **Make the format genuinely pluggable (route everything through `Codec`/a `SerialFormat`).**
  The most flexible option, but it is a larger feature than v1 needs and would keep a public
  format-abstraction surface we have no concrete consumer for. Deferred; can be added without a
  storage migration later if a real need appears.
- **ProtoBuf instead of CBOR.** More compact, but requires stable field numbers and is stricter
  about schema evolution — riskier for documents that change shape over time. Rejected.
- **Bypass serialization, store primitives via MMKV's typed accessors.** Loses enums,
  nullability, nested `@Serializable` blobs, and lists; the Android and iOS MMKV typed APIs also
  differ. Collapses the library to typed SharedPreferences. Rejected (also rejected in earlier
  discussion).
