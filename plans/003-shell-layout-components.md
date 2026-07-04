# Phase 003 — Shell Layout: Header, Sidebar, Footer, TOC

Depends on: 002 (needs tokens/components to build the layout from). Loosely depends on 001
(partials already exist to add layout markup/classes to).

## Scope

Build the actual page chrome layout using CSS Grid/Flexbox: the header bar, the collapsible left
sidebar (structural/CSS-driven show/hide — Phase 004 wires the actual toggle JS and persistence),
the main content pane, and the per-page "On this page" TOC scaffold (CSS layout only — Phase 004
wires the JS that auto-generates entries and does scrollspy). Footer with license link and
contribution CTA. This is where mobile-first responsive breakpoints for the **overall page grid**
are defined (distinct from Phase 002's component-level responsiveness).

## Deliverables

1. **`website/assets/css/layout.css`** — added to `main.css`'s import list (after `components.css`).
   Defines named CSS Grid areas for `header` / `sidebar` / `main` / `toc`, with these mobile-first
   breakpoints (exact values to finalize in this phase, but use these as the working default):
   - **Base (no media query, < 768px — "mobile"):** single column. Sidebar is hidden by default,
     rendered as a full-height overlay/drawer triggered by the hamburger button (`position: fixed`,
     slide-in transform, backdrop). TOC (if the page has one) renders as a collapsed
     `<details>`/toggle block at the top of `<main>`, not as a separate column.
   - **`min-width: 768px` ("tablet"):** decide and document explicitly one of: (a) sidebar becomes
     a permanently visible push-column (main content narrows to accommodate it) while still being
     collapsible via the same hamburger control, or (b) sidebar remains an overlay/drawer but wider
     and less modal-feeling. Recommend (a) — a persistent-but-collapsible sidebar reads better on
     tablet than a mobile-style drawer once there's room. TOC likely stays collapsed at this width
     (promote to aside only at desktop) unless the chosen breakpoint has enough width to spare — 
     decide and document explicitly, don't leave ambiguous.
   - **`min-width: 1200px` ("desktop"):** three-column grid — sidebar | main | TOC aside — all
     visible simultaneously. Main content column gets a `max-width` (e.g. `~72ch` or a fixed
     `px` value) so line length stays readable even as the viewport grows further.
   - **Ultra-wide (no new breakpoint needed if the max-width above is set correctly):** confirm the
     three-column grid doesn't stretch awkwardly — the outer grid can grow, but `main`'s max-width
     keeps prose readable; sidebar/TOC widths stay fixed or capped.

2. **Updated `website/partials/header.html`** — full real markup: logo (inline SVG or `<img
   src="/assets/img/logo.svg">`) linking to `/index.html`; GitHub repo link (`<a href="https://
   github.com/nomemmurrakh/documents">`) with a GitHub icon (inline SVG, self-hosted, no icon-font
   CDN) and adjacent `<span id="gh-star-count" aria-live="polite">​</span>` (empty/placeholder,
   populated by Phase 4's JS; `aria-live="polite"` so screen readers announce the count once it
   loads without being disruptive); search `<input type="search" id="search-input"
   placeholder="Search docs…">` plus an empty results-dropdown `<div id="search-results" role=
   "listbox" hidden>` container; "API Reference ↗" link per the `/api/` convention with an
   external-link icon and `target="_blank" rel="noopener"`; hamburger
   `<button id="sidebar-toggle" aria-expanded="true" aria-controls="sidebar">` with an accessible
   label (`aria-label="Toggle navigation"`).

3. **Updated `website/partials/sidebar.html`** — `<nav id="sidebar" aria-label="Documentation
   sections">` wrapping nested `<ul>`/`<li>` rendered from `nav.json` (Phase 1's `build.js`
   continues to own the actual rendering logic; this phase's job is the surrounding structural/
   layout markup and CSS classes the renderer's output plugs into). Section headers (Guide, Use
   Cases, Concepts, Design Decisions) get `aria-expanded`/`aria-controls` if implemented as
   collapsible sub-trees (decide: are Guide/Use Cases/Concepts always-expanded, or independently
   collapsible? Recommend always-expanded given only ~5-6 children each — collapsing adds
   complexity for little payoff at this scale). Active-page indication (`aria-current="page"`) is
   stubbed here with the attribute present but not yet dynamically set — Phase 4's
   `nav-active.js` sets it at runtime, or Phase 1's `build.js` could set it at build time per page
   (decide which and document — build-time is simpler and requires no JS, recommended).

4. **Updated `website/partials/footer.html`** — license link (`<a href="https://github.com/
   nomemmurrakh/documents/blob/master/LICENSE">License (Apache 2.0)</a>` — confirm this exact
   license type by checking the repo's `LICENSE` file, don't assume) and a "Star/Contribute on
   GitHub" CTA linking to the repo, plus a short one-line encouragement (e.g. "Documents is open
   source — issues, PRs, and stars all help.").

5. **`website/partials/toc.html`** (or an inline template block in `build.js`) — the empty scaffold
   container: `<nav id="toc" aria-label="On this page"><details id="toc-mobile"><summary>On this
   page</summary><ul id="toc-list"></ul></details></nav>` (mobile collapsed form) with CSS
   (Phase 3) switching its rendering to a plain `aside`-positioned list (no `<details>` wrapper
   needed visually, though it can remain in the DOM for progressive enhancement) at the desktop
   breakpoint. Phase 4's JS populates `#toc-list` from the current page's headings.

## Verification

- **375px:** header shows logo + hamburger + a collapsed/icon-only search affordance (or the full
  input if it fits — decide and document which, given real content) + GitHub icon; confirm no
  header overflow/wrap that breaks layout. Sidebar is hidden by default and opens as an overlay/
  drawer on hamburger click (verify by manually toggling the CSS class sidebar-toggle would apply,
  via devtools, since JS isn't wired until Phase 4). TOC (on a stub page with a couple of manually
  added headings for this test) appears as a collapsed block above main content, not a sidebar.
- **768px:** confirm the chosen tablet behavior (documented in deliverable 1) holds — re-check this
  isn't accidentally identical to either the mobile or desktop breakpoint's behavior; it should be
  a deliberate, documented middle state.
- **1200px+:** sidebar, main, and TOC aside all visible as three simultaneous columns; header shows
  all elements inline without wrapping; main content's `max-width` keeps prose from stretching too
  wide even if the browser window is much larger.
- Confirm the footer renders at the bottom of every stub page (from Phase 1) with working license
  and GitHub links (real URLs, not placeholders).
- Confirm the hamburger button and search input are keyboard-focusable (Tab order sane: logo →
  hamburger → search → GitHub link → API reference link, or whatever order the markup naturally
  produces — just confirm it's logical, not scrambled) and that manually toggling
  `aria-expanded`/a CSS class via devtools produces the correct visual show/hide (functional JS
  wiring is Phase 4's job; this phase only confirms the CSS contract is correct).
- Re-run Phase 1's link-check and Phase 2's style-guide checks to confirm nothing regressed.
