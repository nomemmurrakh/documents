# ADR-0021: `FieldDecorator` — a bytes-in/bytes-out extension point for field values

**Status:** Accepted
**Date:** 2026-07-04

## Context

Several requests for per-field behavior beyond storage — encryption, compression, checksums,
logging — surfaced as "decorators." `Storage` (`Storage.kt`) already looks like the natural
seam for this: `getBytes`/`putBytes` operate on bytes, and every implementation
(`MmkvStorage`, `InMemoryStorage`) is written against the interface, not a concrete type.

But `Storage` cannot become that public seam directly. It is `internal` by design
(`architecture.md` §1), and its methods operate on **decomposed storage keys**
(`"{doc}::{field}"`, `Keys.kt`) and raw CBOR bytes — a shape ADR-0015 and `api-design.md` §6
explicitly reserve as a private implementation detail, not a public extension point. Exposing
`Storage` as-is, or a trimmed version of it, would freeze that key/format shape forever;
trimming method count doesn't change which layer is exposed.

A public `Storage`-like extension point where users supply arbitrary decorators was also
considered and rejected as a starting point: it reads as a second product decision ("this is
now an extensible storage framework"), which is not a stated PRD goal, and it is a much larger
commitment than the concrete use cases (encryption, compression, checksums, logging) need.

The design that survived scrutiny sits one layer up: between `Document<T>` and the
CBOR/decomposition step, transforming one already-CBOR-encoded field's bytes at a time. This
sidesteps the `Storage`-freezing problem entirely (no key string, no CBOR internals exposed)
while still giving decorators real per-field targeting.

This ADR is exploratory work promoted to a decision; the full design history — why
`KProperty1<*, *>` was tried and rejected in favor of a plain field name, the survey of
comparable extension points in other libraries (Okio, kotlinx-io, OkHttp `Interceptor`,
SQLCipher, Jetpack Security/Tink `Aead`, DataStore `Serializer<T>`, Room `TypeConverter`), and
the AEAD/associated-data reasoning — is preserved in
`docs/discussions/decorators-and-encryption.md` and is not repeated in full here.

This ADR does **not** promote NG2 (encryption at rest, PRD) out of non-goal status.
`FieldDecorator` is a general extension point that a consumer or a future library feature
*could* use to build encryption; it does not ship encryption itself. A separate,
currently-dormant idea — built-in encryption via a thin passthrough to MMKV's own crypto
support — is tracked independently in `docs/discussions/mmkv-encryption-passthrough.md` and
is unaffected by this decision either way.

## Decision

Add a public interface, `FieldDecorator`, operating on a field's already-CBOR-encoded bytes,
keyed by field name:

```kotlin
public interface FieldDecorator {
    public fun wrap(fieldName: String, bytes: ByteArray): ByteArray
    public fun unwrap(fieldName: String, bytes: ByteArray): ByteArray
}
```

**Parameter is `fieldName: String`, not `KProperty1<*, *>`.** An early draft passed the field's
`KProperty1`, reusing the concept from `update(prop, value)` (ADR-0018), so a decorator could
write `if (property == Profile::ssn) ...`. This does not fit the actual integration point:
`CompositeEncoder`/`CompositeDecoder` (`DocumentEncoder.kt`, `DocumentDecoder.kt`) only ever
have a `SerialDescriptor` + index, i.e. `descriptor.getElementName(index): String` — there is
no `KProperty1` available there without new reflection plumbing this library does not
otherwise need (and Kotlin/Native has no general reflection to derive one from a descriptor).
`FieldDelegate.kt`'s existing `update(prop, value)`/`field(prop, default)` already convert
`KProperty1` down to `prop.name: String` at their own boundary (`fieldDelegate`,
`updateField`), so a plain `String` here matches established convention rather than departing
from it. Targeting one field becomes `if (fieldName == Profile::ssn.name) ...`.

**`ByteArray` in/out, not streaming.** Every comparable extension point surveyed that
transforms one already-materialized value (Tink's `Aead`, Jetpack Security's value
encryption) uses whole-array bytes; streaming interfaces (Okio/kotlinx-io `Source`/`Sink`,
Tink `StreamingAead`, Jetpack Security's `EncryptedFile`) exist only for large/continuous data
(files, HTTP bodies) in every library checked, never for a single small-to-medium value.
`Documents` fields are the latter case — already fully materialized as CBOR bytes by the time
a decorator runs. Streaming was further ruled out on KMP grounds: Okio's `CipherSource`/
`CipherSink`, the class most relevant to encryption, is JVM-only with no iOS implementation.

**Two internal integration points, confirmed against source, not assumed.** Every public
write/read operation in `api-design.md` §10 funnels through exactly one of:

1. `FieldCompositeEncoder.put()` / `FieldCompositeDecoder.read()` + `bytes()`
   (`DocumentEncoder.kt`, `DocumentDecoder.kt`) — backs `set()`, `update { }`, and `get()`,
   since all three drive `encodeDocument`/`decodeDocument` (`DocumentSerialization.kt`), which
   construct these classes.
2. `DocumentImpl.writeField()` / `readField()` (`Document.kt`) — backs the single-field
   `update(prop, value)` (ADR-0018) and the field delegates `field()`/`fieldFlow()`
   (`FieldDelegate.kt`), which bypass the encoder/decoder entirely and call
   `storage.putBytes`/`getBytes` directly.

Both already compute a field-name `String` immediately adjacent to a CBOR encode/decode call
and a `Storage` call — the wrap/unwrap insertion point is the same relative position in both,
so implementation should share one internal helper rather than duplicate the fold logic:

```kotlin
internal fun applyWrap(decorators: List<FieldDecorator>, fieldName: String, bytes: ByteArray): ByteArray =
    decorators.fold(bytes) { acc, d -> d.wrap(fieldName, acc) }

internal fun applyUnwrap(decorators: List<FieldDecorator>, fieldName: String, bytes: ByteArray): ByteArray =
    decorators.foldRight(bytes) { d, acc -> d.unwrap(fieldName, acc) }
```

**Order is forced, not chosen.** `applyWrap` folds the list left-to-right on write;
`applyUnwrap` folds right-to-left on read, so whichever decorator ran last on write runs first
on read. This is the only order that round-trips correctly for an arbitrary chain — not a
style preference.

**Failure contract: reuse `DocumentDecodingException`, no new exception type.** Both
integration points already catch `SerializationException`/`IllegalStateException`/
`IllegalArgumentException` around the CBOR call and wrap them as
`DocumentDecodingException(documentKey, fieldName, cause)` (`DocumentDecoder.kt`'s `decode()`,
`Document.kt`'s `readField()`). `unwrap()` is called immediately adjacent, inside the same
try/catch; a `FieldDecorator` implementation surfaces a failure (bad tag, wrong key, corrupted
input) correctly by throwing one of these already-caught types — documented as the contract in
`FieldDecorator`'s KDoc.

**Attachment: both `collection(...)` and `document<T>(...)`, document appends to collection.**
`Documents.collection(name) { decorators = listOf(...) }` sets a default decorator list
applied to every document opened in that collection; `Documents.document<T>(key) { decorators
= listOf(...) }` appends further decorators for that one document. This matches
`api-design.md` §1's framing of collections as a natural "encryption boundary" while still
letting one sensitive document type add a decorator without forcing a whole separate
collection. This is a new layering pattern in this codebase, not a reuse of an existing one —
`dispatcher`, the only config field today (`DocumentConfig`/`CollectionConfig`, `Documents.kt`),
does not currently layer collection→document, and `Collection.document(key)` does not even
accept a config block.

**Default is `emptyList()`; the no-decorator path costs one empty-list check.**
`decorators: List<FieldDecorator> = emptyList()` on both config types. `fold`/`foldRight` on
an empty list return the initial accumulator with zero iterations and zero lambda
invocations — no allocation beyond the shared empty-list singleton. This holds only if:
`decorators` is a plain `List<FieldDecorator>` (not a polymorphic wrapper with its own virtual
dispatch), and the collection→document merge is computed once, at `Document` construction
time inside `CollectionImpl.document(key)` (alongside where `DocumentImpl` is already built
with `storage`/`cbor`/`dispatcher`) — never recomputed inside `readField`/`writeField` or the
encoder/decoder per call. Under this shape, the added cost is one more constructor field on
`DocumentImpl`/`DocumentEncoder`/`DocumentDecoder`, the same shape as the existing `storage`/
`cbor` fields, with no new per-call cost. This is immaterial next to CBOR encode/decode and
MMKV's memory-mapped I/O, which dominate every operation by orders of magnitude.

**AEAD associated-data binding is a convention for implementations, not part of the
contract.** `fieldName` is enough to derive associated data from (e.g.
`fieldName.encodeToByteArray()`) inside an encryption-flavored decorator, binding a ciphertext
to its own field so it cannot be moved to another field's key and still decrypt (mirroring
Tink's `associatedData` parameter and Jetpack Security's use of the encrypted key as AAD). This
is documented as recommended practice in `FieldDecorator`'s KDoc, not enforced by the
interface — a compression or logging decorator has no use for it.

## Consequences

**Positive**
- A real per-field extension point ships without freezing `Storage`'s internal key/CBOR
  format, preserving ADR-0015's guarantee that the on-disk format can change without breaking
  consumer code.
- Matches the closest real-world precedent found (Tink's `Aead`) rather than inventing a novel
  shape, and reuses the field-name-not-`KProperty1` convention `FieldDelegate.kt` already
  established.
- No new exception type; decoding failures continue to surface as `DocumentDecodingException`
  exactly as `api-design.md` §9 already promises.
- No cost to the existing zero-config path: `decorators` defaults to empty, and the fold-based
  design makes the empty case a no-op check, not a new code path with its own overhead.
- Enables encryption, compression, checksums, or logging to be built as ordinary
  `FieldDecorator` implementations — by us or by consumers — without further API surface.

**Negative / cost**
- **New public API surface**, subject to `checkKotlinAbi` from this point forward:
  `FieldDecorator`, and a new `decorators` property on both `DocumentConfig` and
  `CollectionConfig`.
- **New layering pattern to implement and maintain.** Collection→document decorator-list
  merging has no existing analog in this codebase (`dispatcher` doesn't layer today), so it
  needs its own explicit merge rule and its own tests, not a copy of an existing pattern.
- **Correctness burden shifts partly to decorator authors.** Order-sensitivity
  (compress-then-encrypt, not the reverse), AEAD associated-data binding, and self-describing
  ciphertext output (nonce/tag prepended to the returned bytes) are all conventions documented
  in KDoc, not enforced by the type system. A careless `FieldDecorator` implementation can
  still produce silently-wrong results (e.g. non-AEAD encryption with no tamper detection).
- **No streaming support.** Whole-value `ByteArray` in memory at once is the accepted
  tradeoff for typical config/document-sized fields; a large-blob field would need a
  categorically different, not-yet-designed mechanism, following the same split every
  surveyed library uses (Tink `Aead` vs. `StreamingAead`; Jetpack Security's value encryption
  vs. `EncryptedFile`).

## Alternatives considered

- **Expose a trimmed (e.g. get/set-only) public version of `Storage`.** Rejected — trimming
  method count doesn't change which layer is exposed; `Storage`'s two core methods still
  operate on decomposed keys and raw CBOR bytes, which ADR-0015 already reserves as private.
- **Public `Storage`-like extension point for user-authored decorators (unrestricted).**
  Rejected as the starting design — it is a second product decision (extensible storage
  framework, not narrow document store) not stated as a PRD goal, and a much larger surface
  commitment than the concrete use cases need. Not ruled out forever, but would need its own
  PRD goal and ADR if ever pursued independently of this one.
- **Separate `documents-decorators` module wrapping `Document<T>` via an extension function.**
  A packaging choice, not an alternative to the underlying problem — the satellite module
  would still need some public seam in `:documents` to hook into. Its one real benefit (shipping
  `@RequiresOptIn`/unstable surface from a satellite module while `:documents` core ABI stays
  frozen) can still be applied later on top of `FieldDecorator` if warranted; not needed to
  make this decision.
- **Keep `KProperty1<*, *>` as the parameter instead of `fieldName: String`.** Rejected after
  checking the actual `CompositeEncoder`/`CompositeDecoder` integration point, which never has
  a `KProperty1` available — would require new reflection plumbing this library does not
  otherwise carry, for a benefit (`property == Profile::ssn`) already achievable via
  `fieldName == Profile::ssn.name`.
- **Method names `encode`/`decode`, `toStorage`/`fromStorage`, `write`/`read`, `seal`/`open`.**
  All considered against `wrap`/`unwrap`; `encode`/`decode` collides with CBOR's own
  vocabulary at a different layer, `write`/`read` risks implying streaming semantics already
  ruled out, `seal`/`open` over-fits the interface to the encryption use case specifically,
  `toStorage`/`fromStorage` is accurate but verbose and conflicts with `Storage` as a specific
  type name. `wrap`/`unwrap` was chosen as the most neutral fit across encryption,
  compression, checksums, and logging alike.
- **Attach `decorators` only at the `Collection` level, or only at the `Document` level.**
  Collection-only forces a whole separate collection just to add a decorator to one sensitive
  document type; document-only loses the "encryption boundary at the collection level" framing
  already established in `api-design.md` §1. Both, with document appending to collection, was
  chosen to support either use case without forcing the other.
