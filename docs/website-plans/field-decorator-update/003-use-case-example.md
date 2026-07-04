# 003 — Use Case coverage for FieldDecorator

**Depends on:** nothing structurally, but should stay consistent with 001's encryption framing
(read 001 first if executing out of order — don't need it *done*, just need to match its voice
and technical claims). **Depended on by:** 005 (verification).

**Scope change from the original plan:** the full worked `AesGcmFieldDecorator` example (real
`dev.whyoleg.cryptography` calls, `encryptBlocking`/`decryptBlocking`, AAD binding, key
generation) now lives entirely in **this** sub-plan's output, not on the Guide page. 001's Guide
page (`guide/field-decorators.html`) only covers attaching/ordering/where-it-applies; 001's
Concepts page (`concepts/decorations.html`) has only a trimmed illustrative sketch
(`EncryptingDecorator(cipher: Cipher)`, generic `cipher.encrypt`/`decrypt`, no real library, no
Blocking-suffix detail) and links here for the real thing. This sub-plan is now the single place
the flagship example actually lives — treat the content below as the canonical version of that
example, superseding anything drafted in 001's original version.

## Goal

Decide, explicitly and with justification, whether `FieldDecorator` earns representation in the
Use Cases section, and implement whichever call is made.

## The decision to make

Two options, both legitimate — this sub-plan's job is to pick one and follow through, not to
leave it open:

**Option A — fold into `use-cases/session-and-user-state.html`.** This page already covers
signed-in user/auth/active-profile state in a named collection — a session token or auth
credential is a realistic field to encrypt, and `api-design.md` §1 already frames collections as
a natural "encryption boundary." Add a new section, after the existing sign-in/sign-out content,
with the **full real worked example** (this is now the one and only place it lives — see the
scope-change note above):

```kotlin
// build.gradle.kts (your app/module, not Documents itself)
implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
```

```kotlin
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES

class AesGcmFieldDecorator(
    private val key: AES.GCM.Key,
) : FieldDecorator {

    private val cipher = key.cipher()

    override fun wrap(fieldName: String, bytes: ByteArray): ByteArray =
        cipher.encryptBlocking(
            plaintext = bytes,
            associatedData = fieldName.encodeToByteArray(),
        )

    override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray =
        cipher.decryptBlocking(
            ciphertext = bytes,
            associatedData = fieldName.encodeToByteArray(),
        )
}

val provider = CryptographyProvider.Default
val key = provider.get(AES.GCM).keyGenerator().generateKeyBlocking()

val sessionStore = Documents.collection("session") {
    decorators = listOf(AesGcmFieldDecorator(key))
}
val session = sessionStore.document<Session>("current")
```

Prose to include: cite `cryptography-kotlin` (`dev.whyoleg.cryptography`, v0.6.0) as **a** library
choice, not the only one, and that it's the consumer's own dependency, not one `Documents` pulls
in. Call out `encryptBlocking`/`decryptBlocking` explicitly (not the suspend `encrypt`/`decrypt`)
since `FieldDecorator.wrap`/`unwrap` are synchronous (ADR-0021, ADR-0011). Note briefly (2-3
sentences, not a crypto tutorial) why this is safe: nonce+tag bundled in one `ByteArray` by
`encryptBlocking`, AEAD means tampered ciphertext throws instead of decoding garbage, and
`fieldName` as associated data stops a ciphertext copied to the wrong field from decrypting.
State plainly that key generation/storage/rotation is the consumer's own responsibility, outside
`FieldDecorator`'s scope, linking to the `cryptography-kotlin` docs for that. Cross-link back to
`/concepts/decorations.html` for the underlying mechanism and `/guide/field-decorators.html` for
how attachment/ordering works in general.

**Option B — new 6th Use Case page**, e.g. `use-cases/encrypting-sensitive-fields.html`. Higher
ceremony: needs a `nav.json` entry under `"Use Cases"` children, its own domain example, and adds
to the build/link-check surface. Matches the existing pattern where each Use Case page has its
own domain type, but no other cross-cutting mechanism (not even `update(prop,value)`) got its
own Use Case page in the ADR-0018 precedent — mechanisms fold into existing pages; only
consumer-scenario groupings (Settings, Session, Caches, Reactive UI, Shared KMP) got their own
pages.

**Recommendation to carry into implementation: Option A.** It matches the established pattern
(mechanisms extend existing Use Case pages via a relevant scenario; they don't spawn new ones)
and reuses `Session` and `api-design.md` §1's existing "collection as encryption boundary"
framing rather than inventing a new domain type solely to demonstrate encryption. This
recommendation should be re-confirmed with the user at the start of implementing this sub-plan
(a one-line confirmation is enough — the call was made here but not yet re-validated
interactively), since it's a real content decision, not a mechanical one.

## If Option A (recommended)

- Touch only `website/src/use-cases/session-and-user-state.html`.
- Add a short section (a couple of sentences + one snippet, not a new page-length treatment)
  showing a `Session` field decorated for encryption, using the same
  `dev.whyoleg.cryptography` / `AesGcmFieldDecorator` naming introduced in 001 for consistency
  (don't invent a differently-named decorator class here).
- No `nav.json` change, no new build/link-check surface beyond the new outbound link to
  `/guide/field-decorators.html`.

## If Option B (fallback, only if Option A is rejected when this sub-plan is executed)

- New page `website/src/use-cases/encrypting-sensitive-fields.html`, new domain type (pick
  something realistic, e.g. a `PaymentMethod` or `MedicalProfile`-shaped example — avoid
  reusing `Session`/`Note` so the new page reads as its own scenario).
- New `nav.json` entry under `"Use Cases"` children, after `"Shared KMP Persistence"`.
- Must include a citation-sourcing note per the existing Use Cases convention
  (`007-content-use-cases-and-concepts.md:76-89`'s table format) tracing every factual claim
  back to ADR-0021 / `api-design.md`.

## Acceptance

- Whichever option is chosen, the page(s) build cleanly and pass link-check.
- The encryption framing matches 001/the Decorations Concepts page: this is a consumer-built
  decorator using a named third-party library. Do not state or imply Documents ships encryption
  itself today — but do not invoke PRD NG2 as a permanent non-goal either, since NG2 frames
  built-in encryption at rest as deferred ("surface later"), not ruled out.
- If Option A: confirm `website/src/use-cases/session-and-user-state.html`'s existing content
  isn't disrupted — this is an addition, not a rewrite of the page's existing scenario.
