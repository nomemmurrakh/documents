# Phase 007 — Content: Use Cases & Concepts (10 pages)

Depends on: 001-004. Independent of 005/006/008 content-wise.

## Scope

Author the Use Cases section (5 pages, "why/when would I reach for this") and the Concepts
section (5 pages, "how does it work internally") as one grouped phase, since both lean on the same
source material style (README's "What to use it for" + PRD §2 for Use Cases;
`docs/architecture.md` for Concepts) and benefit from being written together for consistent depth.

All code snippets must match `docs/api-design.md` §10 exactly, consistent with Phase 006's Guide
pages (no contradicting method names between the two phases).

## Use Cases (`website/use-cases/*.html`)

Each expands one bullet from `README.md`'s "What to use it for" section into a fuller page with a
realistic code sketch, cross-linking back to the relevant Guide page(s).

- **`index.html`** — short landing page introducing all 5, linking each.
- **`settings-and-preferences.html`** — the classic `SharedPreferences`/`NSUserDefaults` job, typed
  and observable. Code sketch uses field delegates (`field(prop, default)`) — link to
  `guide/field-delegates.html`.
- **`session-and-user-state.html`** — signed-in user, auth tokens, active profile in a named
  `collection`, so logout is a single `clear`-equivalent (a `delete()` per document, or deleting
  the whole collection — verify the exact mechanism against `docs/api-design.md` before claiming a
  specific "one-call logout" API; if no single "clear whole collection" call exists in the public
  API, don't invent one — describe the actual pattern, e.g. deleting each session document). Link
  to `guide/opening-documents.html` (collections).
- **`caches-and-drafts.html`** — last-synced payloads, in-progress form drafts, "continue where you
  left off" blobs, in a separate collection from durable settings. Link to
  `guide/collections-and-testing.html`.
- **`reactive-ui-state.html`** — anything Compose/SwiftUI should re-render on: `flow()` +
  `collectAsStateWithLifecycle` (the exact snippet pattern from `README.md`), and reference the
  real `sample/` app (Phase 008 covers it in full) as a working example of this pattern. Link to
  `guide/reactivity.html`.
- **`shared-kmp-persistence.html`** — one storage API in `commonMain`, Android and iOS reading/
  writing the same documents through the same code. Sources: `docs/architecture.md` §1 (layered
  view — public API lives entirely in `commonMain`) and §7 (source-set layout). Link to Platform
  Support (Phase 008).

## Concepts (`website/concepts/*.html`)

- **`field-decomposition.html`** — one MMKV entry per top-level field (`{doc}::{field}` key
  format), why decomposition enables partial updates and field-level flows without a
  read-modify-write of the whole object on every field set. Nested `@Serializable` types are
  stored as a single sub-blob under their field's key (not decomposed further in v1). Sources:
  `docs/architecture.md` §3-4; ADR-0001; ADR-0003 (SerialDescriptor walking, no reflection, for
  KMP/native compatibility).
- **`serialization-cbor.html`** — a single internal CBOR format sits between the field-walker and
  `Storage`, encoding each *field value* directly to bytes (no JSON text detour). Not a pluggable
  extension point in v1 — explicitly note this supersedes an earlier `Codec<T>` design (mention
  ADR-0006 was superseded, don't present the old Codec abstraction as available). Sources:
  `docs/architecture.md` §5; ADR-0015 (supersedes ADR-0006).
- **`storage-spi.html`** — the minimal `Storage` interface (`getBytes`/`putBytes`/`remove`/
  `contains`/`keys`) that makes the storage engine swappable — `MmkvStorage` in production,
  `InMemoryStorage` for tests. Reproduce the interface shape accurately from
  `docs/architecture.md` §2 (this is `internal`, not part of the public API surface — frame it as
  "how it's built," not something a consumer implements). Sources: `docs/architecture.md` §2, §7.
- **`reactivity-model.html`** — MMKV has no native change listeners, so reactivity is a
  process-local `MutableSharedFlow<String>` change bus emitting the affected document key after
  every committed write/delete; `flow()` filters the bus for its key and re-reads. Explicitly state
  the consequence: cross-process change notification is **not** provided (this reinforces the
  single-process caveat from Phase 006, don't contradict it). Sources: `docs/architecture.md` §6;
  ADR-0002.
- **`concurrency.html`** — **must get this exactly right:** the document API is fully
  **synchronous and non-suspending** (`get`/`set`/`update`/`delete`/`exists`, field delegates are
  plain synchronous calls — MMKV is memory-mapped, so a read/write is a memory operation, not I/O).
  Writes are serialized per document via a **non-suspending** lock (not a coroutine `Mutex`) so a
  multi-field `update` is atomic from the caller's perspective. Only `flow()`/`stateFlow()`
  *collection* is dispatcher-governed (default `Dispatchers.Default`, since that work is
  CPU-bound) — the document operations themselves are not async and don't depend on that
  dispatcher. Do not describe any operation as `suspend` or imply a coroutine-based locking
  primitive anywhere on this page. Sources: `docs/architecture.md` §9; ADR-0011.

## Citation list (for review)

| Page | Sources |
|---|---|
| Settings & Preferences | README "What to use it for"; guide/field-delegates.html |
| Session & User State | PRD §2; api-design.md §1 (collections) |
| Caches & Drafts | README "What to use it for"; api-design.md §7 |
| Reactive UI State | README Compose snippet; api-design.md §4 |
| Shared KMP Persistence | architecture.md §1, §7 |
| Field Decomposition | architecture.md §3-4; ADR-0001; ADR-0003 |
| Serialization (CBOR) | architecture.md §5; ADR-0015 (supersedes ADR-0006) |
| Storage SPI | architecture.md §2, §7 |
| Reactivity Model | architecture.md §6; ADR-0002 |
| Concurrency | architecture.md §9; ADR-0011 |

## Verification

- Fact-check pass against `docs/architecture.md` and cited ADRs per page.
- **Specifically confirm** the Concurrency page does not claim suspend functions or a coroutine
  `Mutex` anywhere (ADR-0011 explicitly corrects this misconception) — this is the single most
  important correctness check in this phase.
- Confirm the Storage SPI page's interface shape (method names: `getBytes`, `putBytes`, `remove`,
  `contains`, `keys`) matches `docs/architecture.md` §2 verbatim.
- Confirm Use Cases code sketches use method names consistent with Phase 006's Guide pages (no
  contradicting API shapes between the two phases).
- Confirm the single-process/no-cross-process-reactivity caveat (ADR-0019, ADR-0002) is not
  contradicted anywhere in Use Cases — e.g. Session & User State must not imply cross-process
  session sharing works.
- Confirm Serialization (CBOR) doesn't present `Codec<T>`/`KotlinxCodec` as a current, available
  extension point — it must be framed as superseded/historical if mentioned at all.
- Cross-link check both directions: Guide pages (Phase 006) should link into relevant Concepts
  pages and vice versa where it aids understanding (not force-linked everywhere).
