# Phase 006 — Content: Guide (6 pages)

Depends on: 001-004. Independent of 005/007/008 content-wise (can run in parallel with them once
001-004 are done).

## Scope

Author the six Guide pages as one grouped content phase — task-oriented, narrative walkthroughs of
the public API (not a re-listing of `docs/api-design.md`'s sections), cross-linking to relevant
Concepts pages (Phase 007) and ADRs where a design decision is worth a pointer.

All code snippets in every page below must use method names/signatures exactly as they appear in
`docs/api-design.md` §10 — no drift from superseded pre-ADR-0017/0018 shapes (no `MergeStrategy`
enum, no `set(builder)` — it's `update(builder)` now).

## Pages

### `website/guide/opening-documents.html`
Covers `Documents.document<T>(key)` (default store, get-or-open semantics), `Documents.collection
(name)` (separate MMKV file — when to reach for one: wipe-on-logout cache, per-user scoping,
encryption boundary), the reserved `::` separator and key-validation error, and automatic MMKV
initialization (no `Context`, no `MMKV.initialize()`). Sources: `docs/api-design.md` §1;
ADR-0012; ADR-0013; ADR-0016.

### `website/guide/read-and-write.html`
Covers `get()` (null-if-absent, never throws for missing), `set(value)` (replace), `update {
current -> ... }` (builder over current-or-defaults, returns via `copy()`, runs under the write
lock), `update(prop, value)` (single-field write, no read), `delete()`, `exists()`. Explain the
three-call-shape distinction clearly (replace vs. update vs. single-field update) since this is
the API's central nuance. Sources: `docs/api-design.md` §3; ADR-0008; ADR-0017; ADR-0018.

### `website/guide/reactivity.html`
Covers `flow(): Flow<T?>` (cold, emits current value then every committed write, conflated,
null on delete) and `stateFlow(scope): StateFlow<T?>` (hot, shared while subscribed). Include a
Compose-style snippet (`collectAsStateWithLifecycle`, from `README.md`) to show the practical
usage pattern. Sources: `docs/api-design.md` §4; ADR-0002; `docs/architecture.md` §6.

### `website/guide/field-delegates.html`
Covers `field(prop, default): ReadWriteProperty<Any?, V>` and `fieldFlow(prop, default): Flow<V>`
— property-level ergonomics for settings-style usage, why the caller supplies `default` (a
field's declared default isn't recoverable from a `KProperty` at runtime). Frame this page as
"use this when you don't want the whole document" — a natural lead-in to the Settings & Preferences
Use Case (Phase 007) which this page should link to. Sources: `docs/api-design.md` §5; ADR-0010.

### `website/guide/collections-and-testing.html`
Covers `Documents.inMemory()` (in-memory `Collection`, no real MMKV, for unit tests) and the
**single-process-only caveat** stated plainly and unambiguously (storage is always single-process;
sharing a store across OS processes is not supported and can corrupt it — do not soften this into
a vague caveat, state it as a hard constraint). Sources: `docs/api-design.md` §7; ADR-0016;
ADR-0019.

### `website/guide/error-handling.html`
Covers `DocumentDecodingException` — thrown by `get()`/field reads when a stored field can't be
decoded, naming the `documentKey`, the offending `field` (when applicable), and wrapping the
underlying cause (never a bare `SerializationException`/`IllegalStateException`/
`IllegalArgumentException` reaching user code). Explain why this matters given field decomposition:
one field can go bad while the rest of the document stays healthy. Sources: `docs/api-design.md`
§9; ADR-0009.

### `website/guide/index.html`
A short landing page linking the six pages above with a one-line description each, for orientation
(matches the pattern used for Use Cases/Concepts in Phase 007).

## Citation list (for review)

| Page | Sources |
|---|---|
| Opening Documents | api-design.md §1; ADR-0012; ADR-0013; ADR-0016 |
| Read & Write | api-design.md §3; ADR-0008; ADR-0017; ADR-0018 |
| Reactivity | api-design.md §4; ADR-0002; architecture.md §6 |
| Field Delegates | api-design.md §5; ADR-0010 |
| Collections & Testing | api-design.md §7; ADR-0016; ADR-0019 |
| Error Handling | api-design.md §9; ADR-0009 |

## Verification

- Fact-check pass per page against its cited sources.
- Confirm every code snippet's method names/signatures match `docs/api-design.md` §10 exactly —
  spot-check specifically for any leftover pre-ADR-0017/0018 shape (`MergeStrategy`,
  `set(builder)`) since those are exactly the kind of stale pattern that could leak in from
  training data or an outdated mental model.
- Confirm cross-links from Guide pages to Concepts pages (Phase 007, may still be stub files at
  review time if executed out of order — link *targets* must exist as files from Phase 001
  regardless of content-fill order) resolve without 404s.
- Confirm the single-process caveat in Collections & Testing is stated as a hard constraint, not
  hedged language that could read as "usually fine."
- TOC/scrollspy sanity check (Phase 004's feature) on the longest Guide page (likely Read & Write
  or Error Handling) at desktop width — confirm headings are picked up correctly.
