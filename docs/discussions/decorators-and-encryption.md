# Discussion — `FieldDecorator` design

**Status:** Decided and implemented — see [ADR-0021](../adr/0021-field-decorator-extension-point.md)
and `tasks.md` Phase 12 (T12.1–T12.6, all complete 2026-07-05).
**Started:** 2026-07-04

> This is a discussion doc, not a design doc. It records a conversation so we can pick it
> back up later without re-deriving it. If this turns into real work, promote the decided
> parts into an ADR and move the work into `roadmap.md` / `tasks.md`.
>
> A separate, currently-dormant idea — encryption via a thin passthrough to MMKV's own
> built-in crypto support, as a built-in feature rather than a general extension point — is
> tracked independently in `docs/discussions/mmkv-encryption-passthrough.md`. It is not part
> of this design.

---

## 1. The prompt

Could `Documents` support decorators — composable, bytes-in/bytes-out behavior wrapped
around a document field's already-serialized value (encryption, compression, checksums,
logging) — and if so, with how much breaking change?

Narrowed scope for this discussion: **decorators as per-field byte transforms**, sitting
between `Document<T>` and the CBOR/decomposition layer. Not annotation-based field
decorators and not property-delegate decorators (those are different ideas, not explored
here).

## 2. Why not just expose `Storage`

`Storage` is already shaped like the thing a decorator would wrap (`architecture.md` §2):

```kotlin
internal interface Storage {
    fun getBytes(key: String): ByteArray?
    fun putBytes(key: String, value: ByteArray)
    fun remove(key: String)
    fun contains(key: String): Boolean
    fun keys(prefix: String): List<String>
}
```

Only `MmkvStorage` and `InMemoryStorage` implement it today. Everything above `Storage`
(decomposition, CBOR, reactivity, the public API) is written against the interface, not the
concrete type.

But `Storage` is `internal` by design (keeps the KMP surface clean, per `architecture.md`
§1), and it's not just a visibility choice — `getBytes`/`putBytes` operate on **decomposed
storage keys** (`"doc::field"`) and raw CBOR bytes, which `api-design.md` §6 and ADR-0015
explicitly say is not a public extension point. Exposing `Storage` as-is, or even a trimmed
version of it with fewer methods, would freeze that key/format shape forever — trimming
method count doesn't change *which layer* is exposed.

This is why the design below sits at a different layer entirely: between `Document<T>` and
the CBOR/decomposition step, operating on one already-encoded field's bytes at a time,
without ever seeing a storage key string or the CBOR format's internals.

A separate module (e.g. `documents-decorators`) wrapping `Document<T>` via an extension
function was also considered as a packaging alternative — it doesn't avoid this problem, it
just relocates it: the satellite module would still need *some* public seam in `:documents`
to hook into. Its one real benefit, if ever needed: such a seam could ship
`@RequiresOptIn`/unstable from a satellite module while `:documents` core ABI stays frozen.

## 3. The `FieldDecorator` design

```kotlin
public interface FieldDecorator {
    public fun wrap(fieldName: String, bytes: ByteArray): ByteArray
    public fun unwrap(fieldName: String, bytes: ByteArray): ByteArray
}

val doc = Documents.document<Profile>("profile", decorators = listOf(EncryptingDecorator(key)))
```

### How this was arrived at

- Decorators sit **between `Document<T>` and the CBOR/decomposition layer**, operating on
  one already-encoded field at a time — `ByteArray → ByteArray`. This is the layer where
  "could be bytes" (as opposed to the raw `T` field value) actually works, because a
  decorator like encryption can't usefully operate on an arbitrary `Theme` without itself
  forcing a serialize step; operating post-CBOR-encode sidesteps that.
- `wrap`/`unwrap` should not receive the raw storage key string (leaks decomposition
  format), but *should* be able to target behavior per-field. Originally sketched as passing
  a `KProperty1<T, *>` (reusing the concept from `update(prop, value)`, ADR-0018) so a
  decorator could write `if (property == Profile::ssn) ...`. **Revised** after checking the
  actual encoder/decoder (§3.1 below): the real per-field hook point never has a
  `KProperty1` available, only a `SerialDescriptor` + index (`descriptor.getElementName
  (index)`), i.e. a plain field-name `String`. `FieldDelegate.kt`'s existing
  `update(prop, value)`/`field(prop, default)` already convert `KProperty1` down to
  `prop.name: String` at their own internal boundary (`fieldDelegate`/`updateField`), so
  `FieldDecorator` doing the same is consistent with, not a departure from, existing
  convention — and avoids new reflection plumbing. Targeting a specific field becomes
  `if (fieldName == Profile::ssn.name) ... else bytes`.

### Why this shape

- No key string, no CBOR/decomposition internals exposed — satisfies ADR-0015.
- Lets a decorator special-case individual fields (e.g. encrypt only `ssn`).
- Matches the field-name convention `FieldDelegate.kt` already uses internally.
- Composable by list order (`listOf(decorators)`).

### 3.1 Open design questions — resolved

- **Wrap/unwrap order** — **resolved**: write applies the list left-to-right, read applies
  it right-to-left, so a decorator that ran last on write runs first on read:

  ```kotlin
  internal fun applyWrap(decorators: List<FieldDecorator>, fieldName: String, bytes: ByteArray): ByteArray =
      decorators.fold(bytes) { acc, d -> d.wrap(fieldName, acc) }

  internal fun applyUnwrap(decorators: List<FieldDecorator>, fieldName: String, bytes: ByteArray): ByteArray =
      decorators.foldRight(bytes) { d, acc -> d.unwrap(fieldName, acc) }
  ```

  This isn't a preference, it's forced by round-tripping correctness — the reverse order is
  the only order that undoes each wrap in the right sequence.

- **Whole-document writes** (`set(value)`, `update { }`) — **resolved, confirmed against
  actual code**. Verified there are exactly **two** internal choke points, both already
  computing a plain field-name `String` immediately before/after a `Storage` call — the
  natural wrap/unwrap insertion point in both:

  1. `FieldCompositeEncoder.put()` / `FieldCompositeDecoder.read()` + `bytes()`
     (`DocumentEncoder.kt` / `DocumentDecoder.kt`) — backs `set()`, `update { }`, and
     `get()`, since all three ultimately call `encodeDocument`/`decodeDocument`
     (`DocumentSerialization.kt`), which drive these classes via `serializer.serialize`/
     `deserialize`.
  2. `DocumentImpl.writeField()` / `readField()` (`Document.kt`) — backs the single-field
     `update(prop, value)` (ADR-0018) and the field delegates `field()`/`fieldFlow()`
     (`FieldDelegate.kt`), which bypass the encoder/decoder entirely and call
     `storage.putBytes`/`getBytes` directly.

  Both choke points call CBOR encode/decode immediately adjacent to the `Storage` call, so
  `applyWrap`/`applyUnwrap` insert at the same relative position in both — a single shared
  internal helper, not duplicated logic. No third path exists; every public write/read
  operation in `api-design.md` §10 goes through one of these two.

- **Sync-only?** — **resolved**: yes. Consistent with ADR-0011 — a bytes-to-bytes transform
  has no reason to suspend, and both choke points above are already synchronous.

- **Failure contract** — **resolved**: fold into the existing `DocumentDecodingException`
  wrapping, no new exception type. Both choke points already catch a fixed set of exception
  types around the CBOR step and wrap them as `DocumentDecodingException(documentKey,
  fieldName, cause)` (`DocumentDecoder.kt`'s `decode()`, `Document.kt`'s `readField()`).
  Calling `unwrap` immediately adjacent to `cbor.decodeFromByteArray`, inside the same
  try/catch, means a `FieldDecorator` implementation just needs to throw one of the already
  caught types (`SerializationException`, `IllegalStateException`,
  `IllegalArgumentException`) on failure (e.g. bad tag, wrong key) to surface correctly as a
  `DocumentDecodingException` with no new public exception type. This should be stated
  explicitly in `FieldDecorator`'s KDoc as the contract implementations must follow.

- **Per-`Document` vs. per-`Collection`** — **resolved**: both, with `document<T>(...)`
  extending `collection(...)`. `collection(name) { decorators = listOf(...) }` sets a
  default applied to every document opened in that collection; `document<T>(key) {
  decorators = listOf(...) }` can add further decorators on top for that one document.
  Matches `api-design.md` §1's framing of collections as a natural "encryption boundary"
  while still allowing one sensitive document type to add its own decorator without
  forcing a whole separate collection just for it.

  **Note for implementation**: this is a new layering pattern, not a reuse of an existing
  one — `dispatcher` (the only config field that exists today, in `DocumentConfig`/
  `CollectionConfig`, `Documents.kt`) does *not* currently layer between collection and
  document; `Collection.document(key)` doesn't even accept a config block. Introducing
  decorator layering will be the first instance of collection-then-document config
  merging in this codebase, so it needs its own explicit merge rule (append document
  decorators after collection decorators, most likely) rather than assuming one already
  exists to copy.

- **Performance impact when no decorators are configured** — checked explicitly (2026-07-04)
  since `decorators` defaults to empty and this must not tax the common case. Resolved: no
  measurable cost, provided the implementation follows this shape —
  - `decorators: List<FieldDecorator> = emptyList()` as the default on both
    `DocumentConfig`/`CollectionConfig`. An empty list is a shared singleton in Kotlin, not
    allocated per call.
  - `applyWrap`/`applyUnwrap` (§3.1 above) use `fold`/`foldRight` directly over that
    `List<FieldDecorator>` — on an empty list, `fold`'s initial accumulator is returned with
    zero iterations and zero lambda invocations. This is a plain empty-check, not a
    virtual-dispatch indirection, so there's nothing to optimize away later.
  - The collection→document decorator-list merge (previous bullet) must be computed **once**,
    at `Document` construction time (i.e. inside `CollectionImpl.document(key)`, alongside
    where `DocumentImpl` is already built with `storage`/`cbor`/`dispatcher`) — never
    recomputed inside `readField`/`writeField` or the encoder/decoder on every call. Get this
    wrong and every `get()`/`set()` pays a list-concatenation even with zero decorators
    configured.
  - Net: the no-decorator path costs one empty-list check per field read/write, addable to
    `DocumentImpl`/`DocumentEncoder`/`DocumentDecoder` as one more constructor field
    (`decorators: List<FieldDecorator>`) alongside the existing `storage`/`cbor` fields —
    same shape, no new per-call cost. This is immaterial next to CBOR encode/decode and
    MMKV's memory-mapped I/O, which dominate every operation by orders of magnitude.
    Worth a microbenchmark once implemented (extending the existing on-device benchmark
    suite, ADR-0014) to confirm empirically, but no design change is anticipated to be
    needed as a result.

## 4. Research: is `ByteArray` the right currency, and method naming

Before committing to `ByteArray → ByteArray`, researched how comparable "transform bytes
before persistence" extension points are shaped in real-world libraries — Okio, kotlinx-io,
OkHttp `Interceptor`, SQLCipher, Jetpack Security (`androidx.security.crypto`), DataStore
`Serializer<T>`, Room `TypeConverter` — to check whether streaming (`Source`/`Sink`) or some
other shape would serve better than a whole-value `ByteArray`.

### Finding: `ByteArray` in/out is the correct shape, not a shortcut

Every library surveyed that transforms **one already-materialized value** (as opposed to a
continuous stream or a file) uses whole-array bytes:

- **Tink's `Aead`** (the closest real-world analog found) —
  `fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray` /
  `fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray`.
- **Jetpack Security / `EncryptedSharedPreferences`** — built directly on Tink `Aead` for
  individual preference values.
- **`osipxd/encrypted-datastore`'s `EncryptingSerializer`** — even when built on a streaming
  primitive (`StreamingAead`), the small-value path in practice still buffers the whole
  value rather than truly streaming it.

Streaming interfaces (Okio/kotlinx-io `Source`/`Sink`, Tink `StreamingAead`, Jetpack
Security's `EncryptedFile`) appear **only** as a categorically separate interface for large
or continuous data (files, HTTP bodies) — never for a single small-to-medium value. Since
`Documents` fields are exactly the latter (already fully materialized as CBOR bytes by the
time a decorator runs), `ByteArray → ByteArray` matches where the industry actually draws
this line, not a simplification of it.

Streaming was also considered and rejected on KMP grounds specifically: Okio's
`CipherSource`/`CipherSink` — the class most relevant to encryption — is **JVM-only, no iOS
implementation**, which would undercut a KMP-first library. kotlinx-io avoids that trap
(fully `commonMain`) but ships no built-in transforms at all, so it would mean writing the
same decorator code as with `ByteArray`, just wrapped in `Source`/`Sink` state-machine
ceremony (`read`/`write`/`close`/`flush`, partial-read handling) for no benefit at
field-sized values.

Room's `@TypeConverter` and OkHttp's `Interceptor`/`Chain` were also checked and ruled out
as models: `TypeConverter` operates on typed values, not bytes, at a different layer
entirely; `Interceptor`'s `chain.proceed()` shape earns its complexity from HTTP-specific
needs (short-circuiting on cache hits, retries with request mutation, variable pipeline
depth) that don't apply to a fixed, always-fully-applied, always-symmetric field transform.

### Encryption-specific pitfalls this surfaced (apply to any concrete decorator, not the interface)

- **Nonce/IV must travel with the ciphertext.** Convention (Tink, general AEAD practice) is
  for `wrap()` to return `nonce || ciphertext || tag` as one blob, so `unwrap()` needs no
  external state — this is why a plain `ByteArray → ByteArray` signature is sufficient
  without an out-parameter for the nonce.
- **Must be authenticated, not just encrypted.** A concrete decorator should use an AEAD
  mode (e.g. AES-GCM) so tampered/corrupted stored bytes cause `unwrap()` to throw rather
  than silently returning garbage plaintext — fold this into the existing
  `DocumentDecodingException` wrapping (`api-design.md` §9), not a new exception type.
- **Field identity should bind the ciphertext to its field (AAD).** This is the part worth
  spelling out:

  AEAD's integrity guarantee only covers "this ciphertext wasn't tampered with" — it says
  nothing about *where* a valid ciphertext is allowed to be used. Without more, a perfectly
  valid encrypted value can be **moved**: e.g. copy the bytes stored under
  `profile::backupSsn` into `profile::ssn`, and if nothing binds either ciphertext to its
  own field, decryption of the swapped value succeeds — silently returning the wrong field's
  data with no error. The fix is *associated authenticated data* (AAD): extra context fed
  into the cipher alongside the plaintext that isn't itself encrypted but **is** covered by
  the auth tag, so decryption fails unless the same AAD is supplied again. Tink's
  `encrypt`/`decrypt` take `associatedData` explicitly for this; Jetpack Security uses the
  (encrypted) key as AAD for its value, binding each value to its own key.

  `FieldDecorator` already threads `fieldName: String` through both directions — that
  parameter is enough to derive AAD from (e.g. `fieldName.encodeToByteArray()`) inside a
  concrete implementation, with no new API needed:

  ```kotlin
  class AesGcmFieldDecorator(private val key: ByteArray) : FieldDecorator {
      override fun wrap(fieldName: String, bytes: ByteArray): ByteArray {
          val aad = fieldName.encodeToByteArray()
          // seal(bytes, aad) -> nonce || ciphertext || tag, using `aad` as associated data
          return seal(key, aad, bytes)
      }
      override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray {
          val aad = fieldName.encodeToByteArray()
          // open(bytes, aad) throws if the tag doesn't match — including if `aad` differs
          // from what was used to seal, e.g. because this ciphertext was moved from another field
          return open(key, aad, bytes)
      }
  }
  ```

  This stays a convention for encryption-flavored implementations, not a change to the
  `FieldDecorator` contract itself — a compression or logging decorator has no use for AAD,
  so the interface shouldn't force it. Worth stating explicitly in `FieldDecorator`'s KDoc so
  future decorator authors don't have to rediscover it.
- **Compression-then-encryption order, not the reverse.** Encrypted bytes are high-entropy
  and don't compress; `[Compress, Encrypt]` is meaningful, `[Encrypt, Compress]` mostly
  isn't. Since decorator order is caller-controlled (`listOf(decorators)`), this should be
  called out in docs, not left implicit — the same lesson OkHttp's own interceptor-ordering
  docs flag.

### Naming: `wrap`/`unwrap` — locked in

`encode`/`decode` collides with vocabulary the codebase already uses for CBOR serialization
(`encode`s a `T` to bytes) — reusing it for the decorator step (bytes-to-bytes, post-CBOR)
blurs which layer is being discussed. Candidates considered:

| Names | Reads as | Concern |
|---|---|---|
| `wrap` / `unwrap` (**decided**, 2026-07-04) | "wrap the about-to-be-stored bytes, unwrap on the way back" | Matches the chain-of-transforms mental model in §3 directly; distinct from CBOR's `encode`/`decode`; neutral enough to fit compression/logging/checksum decorators too, not just encryption |
| `toStorage` / `fromStorage` | Explicit about direction | Verbose; also slightly misleading since `Storage` is a specific internal type name already, not just "the general idea of storage" |
| `write` / `read` | Short | Collides with `Storage.putBytes`/`getBytes`'s implied vocabulary and with stream I/O connotations (`Source`/`Sink`) that this section already ruled out as the wrong mental model — risks implying streaming semantics that don't apply |
| `seal` / `open` | Very natural for encryption specifically | Reads oddly for a logging or checksum decorator (`loggingDecorator.seal(...)`?) — over-fits the interface to the encryption use case |

Locked-in interface shape (parameter changed from `KProperty1<*, *>` to `fieldName: String`
after §3.1's check against the actual encoder/decoder — see there for why):

```kotlin
public interface FieldDecorator {
    public fun wrap(fieldName: String, bytes: ByteArray): ByteArray
    public fun unwrap(fieldName: String, bytes: ByteArray): ByteArray
}
```

## 5. Open items for when we pick this back up

All design questions from §3.1 are now resolved (2026-07-04): wrap/unwrap order (fold
forward on write, `foldRight` on read), whole-document write handling (two internal choke
points — `DocumentEncoder`/`DocumentDecoder` and `DocumentImpl.writeField`/`readField` —
both confirmed against actual source), failure contract (reuse
`DocumentDecodingException`, no new type), and attachment level (both `collection(...)` and
`document<T>(...)`, document appends to collection's list). `wrap`/`unwrap` naming and the
`fieldName: String` parameter are both locked in.

- [x] `wrap`/`unwrap` naming locked in (§4, 2026-07-04).
- [x] `FieldDecorator` design questions resolved (§3.1, 2026-07-04): wrap/unwrap order,
      whole-document write handling, failure contract, per-`Document`/per-`Collection`
      attachment.
- [x] ADR written: [ADR-0021](../adr/0021-field-decorator-extension-point.md) (2026-07-04),
      covering the interface shape, both choke points, the collection→document
      decorator-list append rule, the `DocumentDecodingException` failure contract, and the
      no-decorator performance analysis.
- [x] Added to `tasks.md` as Phase 12 (T12.1–T12.6, 2026-07-04).
- [x] Implemented, one task at a time with approval before each build/test run (2026-07-05):
      `FieldDecorator` interface + `applyWrap`/`applyUnwrap` fold helpers (T12.1);
      wired into both choke points, `DocumentEncoder`/`DocumentDecoder` and
      `DocumentImpl.writeField`/`readField` (T12.2); `decorators` on `DocumentConfig`/
      `CollectionConfig` with the collection→document append merge, which required giving
      `Collection.document(key)` a config block it didn't have before (T12.3);
      `DocumentDecodingException` failure contract — including a real bug caught by testing,
      where `FieldCompositeDecoder`'s presence-check path called `applyUnwrap` outside any
      try/catch and would have leaked a raw exception instead of wrapping it (T12.4);
      `api-design.md` §1/§10/§11 and KDoc updated, ABI regenerated and green (T12.5); on-device
      benchmark run confirming no measurable regression on the no-decorator path, modulo
      host-machine variance — see `tasks.md` T12.6 for the full comparison (T12.6).
- [ ] Not explored yet: annotation-based field decorators, property-delegate decorators —
      raised as alternate readings of "decorators" during the original conversation but set
      aside in favor of the `FieldDecorator` framing above.
- Separate, dormant idea tracked independently: built-in encryption via MMKV passthrough —
  see `docs/discussions/mmkv-encryption-passthrough.md`.
