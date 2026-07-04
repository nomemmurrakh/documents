# Documents Docs Website — Plan Overview

## Project summary

Build a mobile-first, dark-themed, hand-written HTML/CSS/JS static documentation website for the
`Documents` Kotlin Multiplatform library (`com.nomemmurrakh:documents`), reusing the existing
`brand/` identity and treating `docs/*.md` (PRD, api-design, architecture, ADRs) plus root
`README.md` as the factual source of truth for all site copy. The site gets a proper docs-site UX:
collapsible left nav, header with GitHub star count + search + API reference link, per-page "On
this page" TOC, simple footer.

**Explicit non-goal for this whole project:** deployment. This plan produces a site that is
complete, internally consistent, and previewable from a local static file server (or `file://`).
Hosting, CI/Pages wiring, a custom domain, and a live Dokka-hosting pipeline are a **separate,
later initiative** — not part of any phase below. Phase 10 stops at "ready to deploy," not
"deployed."

## Phase table

| # | Title | Scope (one line) | Depends on | Status |
|---|---|---|---|---|
| 001 | Scaffolding & Build Tooling | `website/` skeleton, build scripts, nav.json, stub pages, brand assets + favicon | — | Done |
| 002 | Design System & CSS Foundations | Tokens, reset, typography, core components, style-guide page | 001 | Done |
| 003 | Shell Layout Components | Header/sidebar/footer/TOC grid layout, mobile-first breakpoints | 002 | Done |
| 004 | Interactive JS Features | Sidebar toggle, TOC scrollspy, GitHub stats fetch+cache, search | 003 | Done |
| 005 | Content: Overview/Install/Quick Start | The 3 front-door pages | 001-004 | Done |
| 006 | Content: Guide | 6 task-oriented API guide pages | 001-004 | Done |
| 007 | Content: Use Cases & Concepts | 5 use-case pages + 5 conceptual pages | 001-004 | Done |
| 008 | Content: Platform/Benchmarks/Sample/ADRs/Contributing | 5 reference-style pages | 001-004 | Done |
| 009 | Responsive QA & Accessibility Pass | Cross-cutting breakpoint + a11y sweep, fix-in-place | 001-008 | Done |
| 010 | Final Polish & Review | Editorial pass, website/README.md, go/no-go review | 001-009 | Done |

Update the Status column by hand as each phase is executed and reviewed.

## Dependency graph

```
001 → 002 → 003 → 004 → { 005, 006, 007, 008 in any order } → 009 → 010
```

005-008 are content phases that touch disjoint files. Their only shared dependency is the finished
shell/JS from 004 (so e.g. the Overview page's contributor-avatars markup matches the real DOM
contract 004 already defined). They can be executed in any order, interleaved, or by different
sessions, without renumbering. The suggested default is file order (005→006→007→008) purely as a
convenience.

## Global conventions (apply to every phase; not repeated in each phase file)

### Website file/folder layout

```
website/
  index.html  installation.html  quick-start.html
  guide/            {index,opening-documents,read-and-write,reactivity,
                      field-delegates,collections-and-testing,error-handling}.html
  use-cases/        {index,settings-and-preferences,session-and-user-state,
                      caches-and-drafts,reactive-ui-state,shared-kmp-persistence}.html
  concepts/         {field-decomposition,serialization-cbor,storage-spi,
                      reactivity-model,concurrency}.html
  platform-support.html  benchmarks.html  sample-app.html
  design-decisions/index.html
  contributing.html
  # no local page for "API Reference" — external link only, see /api/ convention below

  assets/
    css/  tokens.css base.css layout.css components.css utilities.css main.css
    js/   sidebar-toggle.js toc-scrollspy.js github-stats.js search.js nav-active.js
    img/  logo.svg favicon.svg favicon.ico apple-touch-icon.png og-image.png
    fonts/  (empty initially — system font stacks only, no webfonts needed)

  partials/   head.html header.html sidebar.html footer.html toc.html
  data/       nav.json (hand-authored) search-index.json (generated, not hand-edited)
  scripts/    build.js build-search-index.js package.json README.md

style-guide.html            # Phase 2 review artifact; excluded from nav.json and search index
```

### Content-fidelity rule

Every factual claim on every page must trace to a specific file+section in `docs/PRD.md`,
`docs/api-design.md`, `docs/architecture.md`, `docs/adr/*`, or `README.md`. Content-phase plan
files (005-008) include a citation list mapping each page's claims to source files, to make
fact-check review fast. Never invent numbers, features, or guarantees not present in those
sources — especially never imply support for anything under PRD Non-goals (NG1-NG6): no
querying/indexing, no encryption-at-rest claims, no schema migration blocks, no multi-process
guarantees, no cross-document transactions, no inline-value-class field support.

### Mobile-first CSS rule

Base styles (no media query) target the smallest viewport. Every media query adds capability
**upward** (`min-width`), never removes it (`max-width` queries are avoided unless there's no
`min-width` equivalent).

### Dark-only design tokens (fixed values, defined once in Phase 2, consumed everywhere after)

| Token | Value | Role |
|---|---|---|
| `--bg-base` | `#15111c` | Page background (gradient start) |
| `--bg-elevated` | `#1d1420` | Page background (gradient end) / elevated surfaces |
| Brand-mark gradient | `#7F52FF → #C711E1 → #E44857` | Logo and key brand-mark accents only |
| `--accent-grad-start` / `--accent-grad-end` | `#a888ff` / `#e9789d` | UI accent-text gradient (headlines, links, highlights) — distinct from the brand-mark gradient |
| `--text-heading` | `#f4f0f8` | Headings / high-emphasis text |
| `--text-muted` | `#8d84a0` | Secondary / muted text |

Font stacks: sans `-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif`;
mono `ui-monospace, 'SF Mono', 'JetBrains Mono', Menlo, monospace`.

### Zero-CDN rule

No CDN dependencies anywhere — fonts, JS libraries, icons all self-hosted (vendored if a small
library like lunr.js is used — decided in Phase 4). The only external network calls the live site
ever makes are to `api.github.com` (star count, contributor avatars), and those must degrade
gracefully offline/rate-limited. The site must be fully reviewable from a local static server (or
`file://`) with no network at all, except those two GitHub-stats widgets.

### Local preview

Decided and documented once in Phase 1, reused by every phase's verification step: serve
`website/` with a static file server (e.g. `npx serve website` or `python3 -m http.server` from
inside `website/`). Phase 1's plan records the exact chosen command in `website/README.md`.

### `/api/` path convention

The header's "API Reference ↗" link points at a single documented path constant (`/api/`,
relative to the site root) reserved for Dokka's eventual generated output. This project does not
wire that path up to a real Dokka build — it only reserves the convention so a future deployment
phase has exactly one place to make it real.

## Explicitly deferred past Phase 10

- ~~Deployment: GitHub Pages / Netlify / Vercel / any hosting, custom domain.~~ Done: deployed to
  GitHub Pages at `documents.nomemmurrakh.com` via `.github/workflows/pages.yml`.
- ~~CI wiring: no workflow runs `website/scripts/build.js` or publishes anything.~~ Done: see
  `.github/workflows/pages.yml`.
- Making the Dokka `/api/` link resolve to a real, deployed Dokka site.
- Internationalization / localized content.
- Versioned docs for multiple library releases (this site documents `0.1.0` only).
- Analytics / telemetry of any kind.
- Multi-res `favicon.ico` regeneration tooling beyond what Phase 1 sets up once.

All of the above are legitimate future work, intentionally out of scope so this project stays
focused on producing a reviewable, correct, locally-previewable site.
