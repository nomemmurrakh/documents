# Phase 004 — Interactive JS Features

Depends on: 003 (needs the DOM structure/containers for star count, avatars, search input, sidebar
toggle button, TOC container to exist). Independent of Phases 005-008 (content) — these JS
features are built and tested against Phase 1's stub pages; real content layered in later doesn't
require changing this JS, only the data it operates on (search index content, number of headings
on a page, etc.).

## Scope

Implement all client-side JS behavior, vanilla, no framework, no bundler (plain `<script
type="module">` tags using native browser ES module `import`, no build step required to run them
in a browser): sidebar toggle + persistence, TOC auto-generation + scrollspy, GitHub star-count and
contributor-avatar fetch with shared caching/fallback, and client-side search.

## Search implementation decision (made here, not deferred further)

**Decision: hand-rolled search index + matcher, not lunr.js.** Rationale: the site has ~23-25
pages — small enough that a naive substring/token match against `search-index.json` (produced by
Phase 1's `build-search-index.js`) gives adequate relevance without needing lunr's stemming/
ranking machinery. A hand-rolled matcher is zero-dependency (no vendored library to keep in sync,
no license/attribution to carry), trivially small (well under 100 lines), and fully sufficient at
this content scale. If search quality proves inadequate in Phase 9's QA pass, revisit — but start
simple. Implementation: tokenize the query (lowercase, split on whitespace), score each
`search-index.json` record by counting query-token occurrences in `title` (weighted higher) and
`text` (weighted lower), filter zero-score records, sort descending, cap at ~8 results.

## Deliverables

1. **`website/assets/js/sidebar-toggle.js`** — reads `localStorage.getItem('sidebar-collapsed')`
   on load to set initial state (default: expanded on desktop, collapsed/hidden on mobile per
   Phase 3's CSS defaults — JS only needs to override the *persisted user preference*, not
   reimplement the breakpoint defaults). Click handler on `#sidebar-toggle` toggles a CSS class on
   `<body>` or `#sidebar`, updates `aria-expanded`, and writes the new state to `localStorage`.

2. **`website/assets/js/toc-scrollspy.js`** — on page load, queries the current page's `<main>` for
   `h2`/`h3` elements, populates `#toc-list` with anchor links (adding `id` attributes to headings
   that lack one, slugified from heading text), and sets up an `IntersectionObserver` watching
   those headings to highlight the currently-in-view section's TOC entry (add/remove an `.active`
   class). If a page has zero or one heading, hide the TOC container entirely rather than showing
   an empty/single-item list.

3. **`website/assets/js/gh-cache.js`** — a small shared utility: `cachedFetch(key, url, ttlMs)`
   that checks `localStorage` for `{key}` with a stored `{timestamp, data}`, returns cached `data`
   immediately if `Date.now() - timestamp < ttlMs`, otherwise `fetch`es fresh, stores the new
   `{timestamp, data}` on success, and **on fetch failure (network error, non-2xx, or rate-limit
   403) falls back to returning stale cached data if any exists, or `null` if there's no cache at
   all** — callers must handle the `null` case by hiding their UI element gracefully, never
   throwing or leaving a broken/empty state visible.

4. **`website/assets/js/github-stats.js`** — uses `gh-cache.js`'s `cachedFetch` with a 1-hour TTL
   (`3600000`ms) for two calls: `https://api.github.com/repos/nomemmurrakh/documents` (star count
   → `#gh-star-count`) and `https://api.github.com/repos/nomemmurrakh/documents/contributors`
   (avatars → the Overview page's contributors section, rendering each as an `<img>` with the
   contributor's `avatar_url`, `alt="{login}"`, linking to their GitHub profile `html_url`). Both
   calls run independently — a star-count failure must not block avatars from rendering and vice
   versa. On `null` (no data available at all), hide the respective container completely (no
   "loading forever" spinner, no broken-image icons).

5. **`website/assets/js/search.js`** — on page load, `fetch('/data/search-index.json')` (same-
   origin, no caching complexity needed here since it's a same-site static asset, not a rate-
   limited third-party API), builds the in-memory index once. Wires `#search-input`'s `input`
   event to run the matcher (described above) and render results into `#search-results`
   (`role="listbox"`, each result an `role="option"` with page title + a short matched-text
   snippet). Keyboard handling: `ArrowDown`/`ArrowUp` move a highlighted-result cursor, `Enter`
   navigates to the highlighted result's `href`, `Escape` closes the results dropdown. Clicking a
   result navigates there too.

6. **`website/assets/js/nav-active.js`** — only needed if Phase 3's decision was to set
   `aria-current="page"` at runtime rather than at build time. If Phase 3 chose build-time
   (recommended there), this file is a no-op/removed — record in this phase's execution notes
   which path was actually taken so there's no dead file left behind.

7. Updated partials/stub pages: add correctly-ordered `<script type="module" src="...">` tags
   (deferred/module scripts don't block parsing by default, so no explicit `defer` attribute is
   required, but confirm scripts are placed so they don't run before their target DOM elements
   exist — end of `<body>` or `type="module"`'s natural deferral both work; pick one and be
   consistent).

## Verification

- Toggle the sidebar via the hamburger button, reload the page, confirm the collapsed/expanded
  state persisted correctly across the reload.
- On a stub page manually extended with 4-5 headings for this test (or reused once Phase 6 content
  exists, if this phase is sequenced after it), confirm the TOC lists the right headings in order
  and the scrollspy highlight moves to the correct entry as you scroll.
- With network available: load the Overview stub page and confirm the header's star count renders
  a real number and (once Overview has its contributors container, from Phase 1's stub or Phase
  5's real content) contributor avatars render real images linking to real GitHub profiles.
- Simulate failure: use devtools to block `api.github.com` (Network tab request blocking) and
  reload — confirm the star count element and avatars container both hide cleanly with zero
  console errors that would break other page functionality (search, TOC, sidebar must all still
  work with GitHub calls failing).
- Confirm the 1-hour TTL actually works: after a successful fetch, manually edit the stored
  `localStorage` timestamp (via devtools) to be more than an hour old, reload, and confirm a fresh
  fetch fires (visible in the Network tab) rather than serving the stale cache indefinitely.
- Type a query into the search box matching known stub-page titles/content; confirm relevant
  results appear, are keyboard-navigable (arrow keys + Enter), and Enter/click navigates to the
  correct page.
- Confirm the Network tab shows zero requests to any third-party CDN domain — only same-origin
  requests plus `api.github.com`.
