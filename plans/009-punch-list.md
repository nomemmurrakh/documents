# Phase 009 — Punch List

## Fixed during this phase

1. **Tap target too small on GitHub header link** — `.site-header__github` had no explicit
   `min-width`/`min-height`, and its actual rendered size (icon + stacked star count, tight
   padding) landed under the 44px guideline on mobile, where it's always visible (unlike the API
   Reference link, which only appears at 768px+). Fixed: added `min-width: 44px; min-height: 44px;
   justify-content: center` in `assets/css/layout.css`.

## Verified programmatically (no visual browser available in this session)

- **Zero placeholder content** — grepped all shipped pages for "Content pending"/TODO/FIXME:
  none found. All 26 pages have real Phase 005-008 content.
- **Landmarks** — `<header>`, `<nav aria-label="Documentation sections">` (sidebar),
  `<main tabindex="-1">`, `<nav aria-label="On this page">` (TOC), `<footer>` all present and
  correctly labeled on every page template checked (leaf page, landing/index page, Design
  Decisions index).
- **Contrast** — badge colors (`badge--accepted` 6.5:1, `badge--superseded` 5.06:1 against
  `--bg-elevated`) both clear WCAG AA. Body/muted text against both background tokens re-verified
  in Phase 002, unchanged since.
- **Alt text** — logo correctly `alt=""` (decorative, adjacent text label); contributor avatars
  get `alt={login}` set via JS property assignment in `github-stats.js`.
- **aria-live star count** — `textContent` is set exactly once per successful fetch, no repeated
  updates, so `aria-live="polite"` announces once as intended, not on every re-render.
- **No horizontal overflow risk** — `table` and `pre` both use `display: block; overflow-x: auto`
  scoped to their own container (confirmed for Benchmarks' table and every Kotlin code sample).
- **Ultra-wide max-width** — `.main-content` caps at `72ch` from the 1200px breakpoint up,
  confirmed present in `layout.css`.
- **Tap targets** — `.icon-button` (hamburger) is 44×44px exactly; GitHub link fixed above;
  sidebar/TOC links use generous `padding: var(--space-2) var(--space-3)`.
- **Cross-browser feature check** — `:has()` (Chrome 105+, Safari 15.4+) and
  `IntersectionObserver`/CSS Grid (long-standard) are the only modern features in use; no
  polyfills needed for a Chrome/Safari minimum bar.
- **Search index integrity** — 79 records generated from real content, `style-guide.html`
  correctly excluded, HTML entities decode correctly, page-title suffix stripped.
- **Link-check** — 27 pages (26 real + style-guide.html), zero broken internal links.

## Requires user visual sign-off (browser not available in this session)

- 375px / 768px / 1280px+ / 1920px+ checkpoint sweep across real content pages (not just stub
  pages from earlier phases) — especially the longest pages (Read & Write, Error Handling,
  Benchmarks' table, Design Decisions' grouped list).
- Keyboard-only navigation tab order through header search → results → sidebar → TOC → main
  content on Overview and a Guide page.
- Screen-reader spot check (VoiceOver/NVDA) if available.
- Visual confirmation that the two gradients (brand-mark vs. UI-accent) still read as distinct
  now that real content uses them (headings, links) rather than just the style-guide's isolated
  swatches.

Per the established workflow for this project, each of the above visual items should be confirmed
by the user directly in a browser; issues found should be reported back for a targeted fix, same
as Phases 002-008.
