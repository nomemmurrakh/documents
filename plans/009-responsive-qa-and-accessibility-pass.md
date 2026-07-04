# Phase 009 — Responsive/Mobile-First QA & Accessibility Pass

Depends on: 001-008 (all content and interactive features must exist to QA them).

## Scope

A dedicated cross-cutting QA phase across every real page, at a fixed set of representative
viewport widths, plus a baseline accessibility pass. This phase does not add new content or
features — it's a systematic verification-and-fix pass. Bugs found here are fixed within this same
phase (they're typically small CSS/markup fixes, not new features) and recorded in a punch-list.

## Checkpoints

### 375px (mobile)
Every page: no horizontal scroll anywhere (check every content page, not just stub pages — long
code blocks and wide tables (Benchmarks!) are the most likely offenders — confirm they scroll
horizontally *within their own container*, never breaking page-level layout). Tap targets ≥44px
(header buttons, sidebar links, TOC toggle). Sidebar drawer/overlay opens and closes correctly on
every page, not just the ones tested in Phase 003. TOC collapsed block works and doesn't push
content awkwardly. Header doesn't overflow/wrap badly with all its real elements present (logo,
hamburger, search, GitHub icon, star count — this is the first time all of these render with real
data simultaneously, unlike Phase 003's stub-page check).

### 768px (tablet)
The chosen intermediate sidebar/TOC behavior (documented in Phase 003) holds up on every real
content page, especially the longest ones (Guide pages, Benchmarks' table, Design Decisions'
grouped list) — not just the stub page used during Phase 003's own verification.

### 1280px+ (desktop)
Three-column layout (sidebar/main/TOC) reads well on the longest actual content pages. No orphaned
whitespace, no cramped TOC when a page has many headings (check Read & Write and Error Handling,
likely the longest Guide pages).

### Ultra-wide (1920px+)
Main content column has a sane `max-width` — doesn't stretch full-bleed to unreadable line lengths,
even on the pages with the most prose (Overview, Concepts pages).

## Accessibility checklist

- Keyboard-only navigation: Tab through header search → results → sidebar → TOC → main content on
  at least two representative pages (recommend Overview, since it has the most interactive
  elements — search, avatars, links — and a Guide page with a populated TOC).
- Screen-reader landmark check: confirm `<header>`, `<nav>` (both sidebar and TOC, distinctly
  labeled via `aria-label`), `<main>`, `<footer>` are present and correctly used on every page
  template (check one page per section type — a leaf page, a landing/index page, the Design
  Decisions index).
- Contrast check: body text, muted text, and link colors against both `--bg-base` and
  `--bg-elevated` (re-verify Phase 002's spot-check now holds across real content, including any
  new color usage introduced by badges/callouts in real pages, e.g. ADR "Superseded" badges).
- Confirm `alt` text is present and meaningful on all contributor avatar `<img>` elements (Overview
  page) and the logo.
- Confirm the `aria-live="polite"` region for the star count doesn't cause excessive/annoying
  announcements (it should announce once when the value first loads, not on every re-render).

## Cross-browser spot check

At minimum Chrome and Safari (the brand's CocoaPods/iOS-adjacent audience makes Safari a
reasonable minimum bar) at the same breakpoints above — confirm no browser-specific CSS Grid or
`IntersectionObserver` quirks break the layout or TOC scrollspy.

## Deliverables

- Fixes applied directly across `website/assets/css/*.css`, `website/partials/*.html`, and any
  individual content page found to have a page-specific layout bug.
- A punch-list (filled in during execution, kept in this file or an appendix to it) of every issue
  found and its resolution — or, for anything intentionally not fixed, an explicit note with
  rationale (e.g. "IE11 not supported, by design — not a bug").

## Verification

- The punch-list itself resolved to zero open items (or explicitly deferred items with stated
  rationale).
- Re-run Phase 001's link-check across the now-fully-populated site — internal links multiply once
  content phases added cross-links between Guide/Concepts/Use Cases/ADRs; confirm all of them
  still resolve.
- Re-run Phase 004's search verification against the full, real content set (rebuild
  `search-index.json` from real page bodies, not stub placeholders) — confirm realistic queries
  surface the right pages (e.g. searching "field delegate" surfaces the Field Delegates guide page
  and mentions of ADR-0010 where cited).
