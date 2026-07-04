# 001 — New Guide page: Field Decorators, and new Concepts page: Decorations

**Depends on:** nothing. **Depended on by:** 002 (needs both pages' final URLs/headings), 003
(reuses the encryption framing established here).

## Goal

Add two pages, following the site's established Guide-vs-Concepts split (confirmed by re-reading
the existing Guide/Concepts pages against this plan):

- **Guide pages are task-oriented "how do I do X"** — call shapes, attaching things, when to
  reach for one option over another. Deep mechanism is never inlined; it's a "See Concepts" link.
- **Concepts pages are the mechanism in depth** — interface shape, why it's built the way it is,
  the full reasoning a curious reader can opt into.
- **Use Cases pages are one realistic example, no interface teaching.**

`FieldDecorator` (ADR-0021) needs one page of each kind:

- `website/src/guide/field-decorators.html` — **how to use them**: attaching, order (as a
  practical rule), where else it applies.
- `website/src/concepts/decorations.html` — **the concept of decoration at the field level**: the
  interface, why the fold direction is what it is, a trimmed illustrative sketch of encryption as
  a decoration (not the flagship worked example — that lives in a Use Case page per 003).

Naming: the Concepts page is called **"Decorations"**, not "Field Decorators" — it explains the
general concept of decorating field-level bytes, of which `FieldDecorator` is the interface. The
Guide page keeps the name **"Field Decorators"** since that's the type/API surface being used.

## Nav placement

`website/data/nav.json`, two insertions:

Guide, after `"Field Delegates"`, before `"Collections & Testing"`:
```json
{ "title": "Field Delegates", "href": "/guide/field-delegates.html" },
{ "title": "Field Decorators", "href": "/guide/field-decorators.html" },
{ "title": "Collections & Testing", "href": "/guide/collections-and-testing.html" },
```

Concepts, after `"Serialization (CBOR)"`, before `"Storage SPI"`:
```json
{ "title": "Serialization (CBOR)", "href": "/concepts/serialization-cbor.html" },
{ "title": "Decorations", "href": "/concepts/decorations.html" },
{ "title": "Storage SPI", "href": "/concepts/storage-spi.html" },
```

Justification: Field Decorators sits with the other per-field Guide mechanisms (Field Delegates)
before the broader-scope Collections & Testing/Error Handling pages. Decorations sits next to
Serialization (CBOR) in Concepts because decorations run immediately adjacent to the CBOR
encode/decode step, before the more infrastructural Storage SPI/Reactivity/Concurrency trio.

## Content outline — Guide page (`guide/field-decorators.html`)

`<h1>Field Decorators</h1>` — one-sentence intro: hook into the read/write path per field, wrap
before storage, unwrap after; use for encryption, compression, checksums, logging.

`<h2>Attaching decorators</h2>` — collection-level vs document-level config, append rule, same
`Note`-based snippet as before:
```kotlin
val notes = Documents.collection("notes") {
    decorators = listOf(LoggingDecorator())
}
val note = notes.document<Note>("note-1") {
    decorators = listOf(ChecksumDecorator())
}
// note's effective decorator list, in order: [LoggingDecorator(), ChecksumDecorator()]
```

`<h2>Order matters</h2>` — kept as originally drafted, including the fold diagram (user
explicitly confirmed this section is right as-is, not to be trimmed further):
```kotlin
// decorators = listOf(Compress, Encrypt)
// write:  bytes  -> Compress.wrap   -> Encrypt.wrap   -> stored
// read:   stored -> Encrypt.unwrap  -> Compress.unwrap -> bytes
```
One sentence on why compress-then-encrypt is the order that works, then a link to
`/concepts/decorations.html` for *why* the library folds this way.

`<h2>Where else this applies</h2>` — field delegates honor decorators too (link
`field-delegates.html`); decorators sit next to CBOR encode/decode (link
`serialization-cbor.html`, `field-decomposition.html`); a throwing `unwrap` surfaces as
`DocumentDecodingException` (link `error-handling.html` — do not re-explain, the explanation now
lives on that page per this same sub-plan).

**Explicitly removed from the Guide page** (moved elsewhere): the bare interface listing, the
full AES-GCM example, the compression code example, and the dedicated "Failures" section — see
below for where each now lives.

## Content outline — Concepts page (`concepts/decorations.html`)

`<h1>Decorations</h1>` — one-sentence framing: a bytes-in/bytes-out transform on one field's
already-CBOR-encoded value, between `Document<T>` and storage; this is the mechanism behind
`FieldDecorator` (link back to the Guide page for how to attach one).

`<h2>The interface</h2>` — the real interface:
```kotlin
interface FieldDecorator {
    fun wrap(fieldName: String, bytes: ByteArray): ByteArray
    fun unwrap(fieldName: String, bytes: ByteArray): ByteArray
}
```
Explain `wrap`/`unwrap` timing and that `fieldName` is the field's own name, never a raw storage
key.

`<h2>Why fold in opposite directions</h2>` — the `fold`/`foldRight` mechanics from
`FieldDecorator.kt`, explained as "layers": last wrapped on write = outermost = first unwrapped on
read. Restate the compress-then-encrypt reasoning here in more depth than the Guide page's
one-liner.

`<h2>A sketch of encryption as a decoration</h2>` — a **trimmed, illustrative** sketch (not the
full production `AesGcmFieldDecorator` — that's the Use Case page's job per 003), e.g. a
`EncryptingDecorator(cipher: Cipher)` shape showing `wrap`/`unwrap` calling generic
`cipher.encrypt`/`decrypt` with `fieldName` as associated data, explaining *why* AAD binding
matters (a value copied from one field's key to another fails to decrypt instead of silently
succeeding with the wrong field's data). Do not add a "Documents doesn't ship encryption"
non-goal disclaimer here — dropped per user decision, since built-in MMKV encryption may be
introduced later and this line shouldn't preemptively frame it as ruled out. Close with a link to
the full worked example on `/use-cases/session-and-user-state.html` (per 003's Option A).

`<h2>Failure contract</h2>` — short: `unwrap` throwing `SerializationException`/
`IllegalStateException`/`IllegalArgumentException` surfaces as `DocumentDecodingException`, link
to `error-handling.html`.

## Failures content — merged into `guide/error-handling.html`, not a new section here

Per user decision: add 2-3 sentences to the **existing** "What it wraps, and why" section on
`error-handling.html` (no new `<h2>`), noting that a configured field decorator's `unwrap`
throwing one of the same three exception types is wrapped identically — indistinguishable from
any other decode failure. This is a small addition to an existing Guide page, done as part of
this sub-plan (not deferred to 002), since it's tightly coupled to what this sub-plan removes
from the Guide page.

## Acceptance

- `website/src/guide/field-decorators.html` exists, matches the trimmed outline above — no
  interface listing, no AES-GCM code, no compression code, no dedicated Failures section.
- `website/src/concepts/decorations.html` exists, matches its outline above — holds the interface,
  fold mechanics, and a trimmed (not flagship) encryption sketch.
- `website/data/nav.json` updated with both new entries in the positions specified.
- `website/src/guide/error-handling.html` has the short decorator-failure addition merged into
  its existing section.
- Running `node website/scripts/build.js` succeeds and produces both new built pages.
- `node website/scripts/link-check.js` reports no broken links introduced by either page.
- Every code sample compiles against the actual `FieldDecorator`/`DocumentConfig`/
  `CollectionConfig` API as implemented (cross-check against
  `documents/src/commonMain/kotlin/com/nomemmurrakh/documents/FieldDecorator.kt` and
  `Documents.kt` at implementation time).
