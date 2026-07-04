# Phase 001 — Project Scaffolding & Build Tooling

Depends on: nothing (first phase). See `000-overview.md` for global conventions, the full
`website/` file layout, design tokens, and the nav structure referenced below — this file does not
repeat them.

## Scope

Establish the `website/` directory skeleton, a tiny Node-based build/templating script (no
framework, no bundler), the partials mechanism, `data/nav.json`, brand-asset ingestion, and the
local-preview workflow. Produce placeholder/stub HTML pages for every nav entry so later phases
fill in styling/behavior/content against a working skeleton rather than building the skeleton and
content simultaneously.

## Why build-time templating, not client-side includes

Decision (binding for this project): `website/scripts/build.js` assembles each final page from
`partials/*.html` + the page's own body content at build time, writing plain static HTML files.
Rejected alternative: client-side `fetch`-and-inject includes for header/sidebar/footer — this
would cause a flash-of-unstyled-nav on every page load and make the site's core navigation
depend on JS working. Build-time assembly keeps the site's structure and navigation fully
functional with JavaScript disabled; only three features are genuinely JS-dependent (search, live
GitHub stats, sidebar-persistence/TOC-scrollspy niceties), all deferred to Phase 004.

## Deliverables

1. **`website/data/nav.json`** — single source of truth for the sidebar tree. Structure:
   ```json
   [
     { "title": "Overview", "href": "/index.html" },
     { "title": "Installation", "href": "/installation.html" },
     { "title": "Quick Start", "href": "/quick-start.html" },
     { "title": "Guide", "children": [
       { "title": "Opening Documents", "href": "/guide/opening-documents.html" },
       { "title": "Read & Write", "href": "/guide/read-and-write.html" },
       { "title": "Reactivity", "href": "/guide/reactivity.html" },
       { "title": "Field Delegates", "href": "/guide/field-delegates.html" },
       { "title": "Collections & Testing", "href": "/guide/collections-and-testing.html" },
       { "title": "Error Handling", "href": "/guide/error-handling.html" }
     ]},
     { "title": "Use Cases", "children": [
       { "title": "Settings & Preferences", "href": "/use-cases/settings-and-preferences.html" },
       { "title": "Session & User State", "href": "/use-cases/session-and-user-state.html" },
       { "title": "Caches & Drafts", "href": "/use-cases/caches-and-drafts.html" },
       { "title": "Reactive UI State", "href": "/use-cases/reactive-ui-state.html" },
       { "title": "Shared KMP Persistence", "href": "/use-cases/shared-kmp-persistence.html" }
     ]},
     { "title": "Concepts", "children": [
       { "title": "Field Decomposition", "href": "/concepts/field-decomposition.html" },
       { "title": "Serialization (CBOR)", "href": "/concepts/serialization-cbor.html" },
       { "title": "Storage SPI", "href": "/concepts/storage-spi.html" },
       { "title": "Reactivity Model", "href": "/concepts/reactivity-model.html" },
       { "title": "Concurrency", "href": "/concepts/concurrency.html" }
     ]},
     { "title": "Platform Support", "href": "/platform-support.html" },
     { "title": "Benchmarks", "href": "/benchmarks.html" },
     { "title": "Sample App", "href": "/sample-app.html" },
     { "title": "Design Decisions", "children": [
       { "title": "Overview", "href": "/design-decisions/index.html" }
     ]},
     { "title": "API Reference", "href": "/api/", "external": true },
     { "title": "Contributing", "href": "/contributing.html" }
   ]
   ```
   (`use-cases/index.html` and `guide/index.html` exist as landing pages per Phases 6-7 but are
   not separate nav entries — the section header itself links to its first child or to the
   index; decide exact click-target behavior in Phase 3, not here.)

2. **`website/scripts/build.js`** — Node script (no dependencies beyond Node's stdlib, or at most
   one tiny templating helper) that:
   - Reads `partials/head.html`, `partials/header.html`, `partials/sidebar.html`,
     `partials/footer.html`, `partials/toc.html`.
   - Reads `data/nav.json` and renders `sidebar.html`'s nav tree from it (simple string
     substitution or a minimal template loop — no dependency on a full templating engine needed
     for this scale).
   - For each page source (a `<title>` + body HTML fragment), assembles the final static HTML
     file: `<!doctype html><html><head>{head}</head><body>{header}{sidebar}<main>{body}{toc-slot}</main>{footer}</body></html>`.
   - Writes output directly into `website/*.html` (page sources can live in this same location as
     the final output for this phase, since content is just placeholder — Phase 5-8 plans decide
     whether real content lives as separate source fragments or is edited in place; document
     whichever this phase picks so later phases follow it consistently).
   - Exits non-zero with a clear message on any missing partial/nav entry mismatch.

3. **`website/scripts/build-search-index.js`** — walks the generated `website/**/*.html` (excluding
   `style-guide.html` and anything outside the documented content set), extracts page title +
   visible text content, and writes `website/data/search-index.json` as an array of
   `{ title, href, section, text }` records (one per page, or one per heading-section within a
   page — decide granularity now; recommend one record per `<h1>`/`<h2>` section for better search
   relevance, matching what Phase 4's search UI will want). This script only produces the index;
   Phase 4 builds the UI that consumes it. Running it against stub pages in this phase should
   produce a valid (if sparse) JSON file, proving the pipeline works end-to-end before real content
   exists.

4. **`website/scripts/package.json`** — records any dev dependency actually used (ideally zero;
   Node's built-in `fs`/`path`/an HTML-text-extraction approach using a simple regex or a tiny
   HTML-parsing lib only if strictly needed). No production dependency — this is a build-time-only
   tool, never shipped to the browser.

5. **`website/partials/head.html`, `header.html`, `sidebar.html`, `footer.html`, `toc.html`** —
   structurally complete, semantically correct HTML (correct landmark elements: `<header>`,
   `<nav>`, `<footer>`; ARIA attributes stubbed where Phase 3/4 will need them) but **unstyled** —
   no CSS classes requiring Phase 2/3 CSS to look correct yet, just valid, accessible markup.
   `header.html` includes: logo `<img>`/inline SVG referencing `assets/img/logo.svg`, a GitHub
   repo link (`https://github.com/nomemmurrakh/documents`) with a `<span id="gh-star-count">`
   placeholder, a `<form>`/`<input type="search">` search box (non-functional until Phase 4), an
   "API Reference ↗" `<a href="/api/" target="_blank" rel="noopener">` per the `/api/` convention
   in `000-overview.md`, and a `<button id="sidebar-toggle" aria-expanded="true">` hamburger
   control (non-functional until Phase 4).

6. **One stub HTML page per nav leaf** (all ~23 pages from `nav.json`, plus `guide/index.html` and
   `use-cases/index.html` landing stubs) — each with a correct `<title>`, correct relative asset
   paths (verify these work whether the site is served from root or a subpath — prefer root-
   relative `/assets/...` paths for this phase, consistent with `nav.json`'s hrefs), and a
   placeholder `<h1>` matching its nav title plus a one-line "Content pending — see plans/00X" note
   (to be deleted once real content lands).

7. **Brand asset ingestion into `website/assets/img/`**:
   - Copy `brand/logo.svg` → `website/assets/img/logo.svg`.
   - Copy `brand/exports/logo-512.png`, `logo-256.png` → `website/assets/img/` (used where a raster
     logo is simpler than inline SVG, e.g. `og:image` fallback).
   - Copy `brand/exports/apple-touch-icon-180.png` → `website/assets/img/apple-touch-icon.png`.
   - Copy `brand/exports/social-preview.png` → `website/assets/img/og-image.png`.
   - Generate `website/assets/img/favicon.ico`: this machine has `rsvg-convert` installed (verified
     — no ImageMagick). Generate `favicon-16.png`/`favicon-32.png` via
     `rsvg-convert -w 16 -h 16 brand/logo.svg -o favicon-16.png` (already documented in
     `brand/README.md`, both exports likely already exist under `brand/exports/`), then combine
     into a multi-resolution `.ico` using a small npm tool (`png-to-ico favicon-16.png
     favicon-32.png > favicon.ico`, run via `npx` so no persistent dependency is added) rather than
     ImageMagick's `convert`, since that isn't installed here. Document the exact command used in
     `website/README.md` so it's reproducible if `brand/logo.svg` ever changes.
   - Also copy `brand/logo.svg` as `website/assets/img/favicon.svg` for browsers supporting SVG
     favicons (progressive enhancement over the `.ico`).

8. **`website/README.md`** — documents: how to run the build (`node scripts/build.js && node
   scripts/build-search-index.js`), how to preview locally (the exact static-server command
   chosen, e.g. `npx serve website` run from the repo root, or `python3 -m http.server 8000` run
   from inside `website/`), and how brand assets were derived (the exact `rsvg-convert` + `png-to-
   ico` commands from step 7, so a future `brand/` change can be re-synced by re-running the same
   steps).

## Verification

- Run the documented build command with zero errors; run the search-index script with zero
  errors, producing a valid (if sparse, since content is stub) `search-index.json`.
- Serve `website/` locally using the documented command; confirm all ~23+2 pages load at their
  correct URLs with no 404s. Do a link-check pass: extract every `href`/`src` from every generated
  page and confirm each resolves to an existing file (a simple Node script counts as sufficient
  tooling here — it doesn't need to be a published library).
- Confirm the sidebar (rendered from `nav.json`) lists the full, correctly nested nav tree on every
  stub page, matching the structure in `000-overview.md` exactly (section groupings: Guide / Use
  Cases / Concepts / Design Decisions; flat entries: Overview, Installation, Quick Start, Platform
  Support, Benchmarks, Sample App, API Reference, Contributing).
- Open a stub page in a browser tab; confirm `favicon.ico`/`favicon.svg` renders in the tab, and
  that `apple-touch-icon.png` is correctly referenced in `<head>` (inspect via devtools, exact
  "add to home screen" preview isn't required at this phase).
- Grep every generated page's `<link>`/`<script src>` attributes for `http://`/`https://` — must
  return zero hits (no CDN dependency exists yet at this phase; the only future external calls are
  `api.github.com`, added in Phase 4).
- Confirm `website/README.md`'s documented commands actually work when run fresh (re-run them
  verbatim as written, don't just trust memory of what was run during development).
