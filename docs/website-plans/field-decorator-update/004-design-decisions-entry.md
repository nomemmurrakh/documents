# 004 — Add ADR-0021 to the Design Decisions index

**Depends on:** nothing. **Depended on by:** 005 (verification).

## Goal

`website/src/design-decisions/index.html` is a curated, hand-grouped index of ADRs, currently
covering ADR-0001 through ADR-0020 across five `<h2>` categories (API shape; Storage &
serialization; Reactivity & concurrency; Platform & publishing; Errors & testing). ADR-0021 is
a real, accepted ADR and isn't represented — this sub-plan adds it.

## Category choice

**"API shape"** — the same category as ADR-0018 (`update(builder)`/`update(prop,value)`).
Justification: `FieldDecorator` is a new public interface plus new configuration surface
(`decorators` on `DocumentConfig`/`CollectionConfig`) — it changes what the public API looks
like and how consumers configure documents/collections, which is exactly what the other five
entries in this category are about (vocabulary, factory shape, builder semantics, entry point,
merge strategy, update verbs). It is not primarily a storage/serialization change (it doesn't
touch the CBOR format or key decomposition itself — it wraps around them) and not primarily a
reactivity/concurrency change (ADR-0021 confirms it's sync-only, consistent with existing
concurrency decisions, not a new one), so those two categories don't fit as well.

No new `<h2>` category is warranted — "API shape" fits without strain.

## Exact change

Add one `<li>` to the existing `<ul>` under `<h2>API shape</h2>`
(`website/src/design-decisions/index.html`), **after** the ADR-0018 line, following the exact
existing bullet format (link text `ADR-00XX`, em dash, lowercase-led one-line summary, no
trailing period pattern inconsistency — match neighboring bullets exactly):

```html
<li><a href="https://github.com/nomemmurrakh/documents/blob/master/docs/adr/0021-field-decorator-extension-point.md">ADR-0021</a> — <code>FieldDecorator</code>, a bytes-in/bytes-out extension point for per-field encryption, compression, or logging.</li>
```

No `badge badge--superseded` span — ADR-0021 doesn't supersede or get superseded by anything.

## Acceptance

- The new `<li>` appears under "API shape", after ADR-0018, matching existing bullet
  formatting exactly (same link structure, same em-dash-separated summary style).
- `node website/scripts/build.js` and `node website/scripts/link-check.js` still pass (this is
  an external GitHub link, exempt from local file-existence checks but still worth confirming
  the URL path matches the actual committed ADR filename:
  `docs/adr/0021-field-decorator-extension-point.md`, confirmed present in this repo as of
  commit `cc60029`).
- No other section of the page is touched.
