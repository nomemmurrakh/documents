# 002 — Teasers and cross-links on existing pages

**Depends on:** 001 (needs the final `/guide/field-decorators.html` and `/concepts/decorations.html`
URLs and section headings to link to specific anchors, not just the pages). **Depended on by:**
005 (verification).

**Note:** the `guide/error-handling.html` cross-link that would otherwise belong here was moved
into 001 instead, since it's tightly coupled to content 001 removes from the Guide page — see
001's "Failures content" section. Do not duplicate that touch here.

## Goal

Propagate awareness of the new Guide page into existing pages, mirroring exactly how
`update(prop, value)` (ADR-0018) propagated after it landed: a light teaser on front-door pages,
short cross-reference stubs on pages whose behavior is affected, and nothing more. This is a
surgical pass — each touch should be one sentence or one small paragraph, not a re-explanation
of what's already fully covered on the new Guide page.

## Touches (each is independent within this sub-plan; no page depends on another)

### `website/src/index.html` (landing page)

ADR-0018's `update(prop, value)` got a teaser code snippet in the landing-page walkthrough
(`note.update(Note::done, true) // one field, no read`); CBOR did not, since it's a more
internal mechanism, not something a first-time reader needs to know exists.

FieldDecorator is closer to the ADR-0018 case in "hook appeal" (the encryption angle is a real
draw) but is a more advanced/opt-in feature than a core call shape — decision: **one-line
teaser + link, not a code snippet**, added near wherever the page currently lists library
capabilities/features (read the current file at implementation time to find the natural slot —
likely a bullet list or feature-highlight section). Suggested phrasing direction (not final
copy): "Need per-field encryption, compression, or logging? `FieldDecorator` lets you hook into
the read/write path — see [Field Decorators](/guide/field-decorators.html)."

### `website/src/guide/read-and-write.html`

This page is the canonical explanation of `get()`/`set(value)`/`update{}`/`update(prop,value)`/
`delete()`. Add one short paragraph (not a new `<h2>`, just appended prose near the end of the
page, after all call shapes are covered) noting that every one of these operations passes
through any configured `FieldDecorator`s before/after the CBOR step, with a link to
`/guide/field-decorators.html`. Do not re-explain wrap/unwrap order or the encryption example
here — that's what the link is for.

### `website/src/guide/field-delegates.html`

This page already has a precedent cross-reference section for `update(prop, value)` (a
`<h2>Not inside a class with a delegate? Use <code>update(prop, value)</code></h2>` stub).
Add a comparable short cross-reference — either its own small `<h2>` or a closing paragraph —
noting that `field()`/`fieldFlow()` delegates also honor configured decorators (they call
`readField`/`writeField` internally, the same choke point `update(prop, value)` uses), linking
to `/guide/field-decorators.html`. Keep it to 1-2 sentences, matching the existing stub's
brevity.

### `website/src/concepts/serialization-cbor.html` and `website/src/concepts/field-decomposition.html`

**Superseded by 001**: the deep conceptual home for decorators is now the new
`concepts/decorations.html` page (written in 001), not a stub on these two pages. This touch is
now smaller than originally scoped — one sentence on each of `serialization-cbor.html` and
`field-decomposition.html` noting that decorations (see
`/concepts/decorations.html`) run immediately adjacent to this step. Do not re-explain the fold
mechanics here — that's `decorations.html`'s job now.

## Explicitly out of scope for this sub-plan

- Use Cases pages — covered separately in `003-use-case-example.md`.
- `website/src/design-decisions/index.html` — covered separately in
  `004-design-decisions-entry.md`.
- Any new domain object or new code example beyond what's already on the new Guide page — this
  sub-plan only adds links and short framing sentences.

## Acceptance

- Each touched page still builds (`node website/scripts/build.js`) and passes
  `node website/scripts/link-check.js`.
- No touched page's word count grows by more than a short paragraph (roughly 2-4 sentences) —
  if a touch is trending longer than that, it belongs on the Guide page instead, not here.
- Every link added resolves to `/guide/field-decorators.html` or a specific anchor within it
  that actually exists after 001 is implemented.
