# Phase 002 — Design System & CSS Foundations

Depends on: 001 (needs `website/` skeleton and asset paths to exist). See `000-overview.md` for
the fixed design-token values, font stacks, and mobile-first rule — reproduced here only where a
CSS file needs the literal values.

## Scope

Build the mobile-first CSS foundation: design tokens (color, type scale, spacing, radii, shadows)
as CSS custom properties matching the locked dark palette, a minimal reset, base typography using
the brand's font stacks, and core reusable components (buttons, code blocks, callout/admonition
boxes, tables, badges/pills, the Kotlin-gradient accent treatment echoing the logo). This phase
does **not** build the header/sidebar/TOC layout grid (that's Phase 003) — it builds the visual
language those components will be assembled from. Produces a non-interactive style-guide review
page.

## Deliverables

1. **`website/assets/css/tokens.css`** — CSS custom properties on `:root`:
   ```css
   :root {
     --bg-base: #15111c;
     --bg-elevated: #1d1420;
     --brand-grad-1: #7F52FF;
     --brand-grad-2: #C711E1;
     --brand-grad-3: #E44857;
     --accent-grad-start: #a888ff;
     --accent-grad-end: #e9789d;
     --text-heading: #f4f0f8;
     --text-body: /* a value between --text-heading and --text-muted for body copy, pick and
                     justify in this phase — not literally --text-heading for every paragraph */;
     --text-muted: #8d84a0;
     --font-sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
     --font-mono: ui-monospace, 'SF Mono', 'JetBrains Mono', Menlo, monospace;
     /* spacing scale, type scale, radii, shadow tokens: define a small consistent set,
        e.g. --space-1 through --space-8 on a 4px or 8px base, --radius-sm/md/lg,
        --shadow-elevated for cards/dropdowns */
   }
   ```
   **Explicit rule to document in this file's comments (one short line, per the no-comments
   convention only where genuinely non-obvious):** the brand-mark gradient
   (`--brand-grad-1/2/3`) is reserved for the logo and small brand-mark accents only (e.g. a
   heading's decorative underline on the Overview page); the UI accent-text gradient
   (`--accent-grad-start/end`) is what's used for interactive/emphasis UI text (links, active nav
   state, headline highlights). These must never be swapped — the brand gradient is louder/more
   saturated and reads as "logo," not "UI chrome."

2. **`website/assets/css/base.css`** — reset (box-sizing, margin/padding zeroing, sane defaults for
   `img`/`button`/`a`), root font-size and line-height set mobile-first (no media query), body
   background using `--bg-base`→`--bg-elevated` (as a subtle gradient or the two used for
   base/elevated surfaces — decide and document which), base link styling using the UI accent
   gradient or a solid accent color for non-gradient contexts (e.g. `color-mix` or a solid
   `--accent-grad-start` fallback for browsers without gradient-text support).

3. **`website/assets/css/components.css`** — buttons (primary/secondary), `pre`/`code` blocks
   (monospace, adequate padding/line-height, horizontal scroll within their own container per the
   zero-page-overflow rule enforced fully in Phase 9), inline `code`, callout/admonition boxes (at
   minimum an "info" and a "note" variant, useful for e.g. the single-process caveat callouts
   planned in later content phases), tables (used by Platform Support and Benchmarks pages later),
   badges/pills (used for e.g. ✅ platform-support marks, "Accepted"/"Superseded" ADR status
   badges).

4. **`website/assets/css/utilities.css`** — a small set of helper classes actually used elsewhere
   (visually-hidden/sr-only, text-muted, container/max-width wrapper) — kept minimal, not a full
   utility framework.

5. **`website/assets/css/main.css`** — single entry point that `@import`s the above in cascade
   order (`tokens` → `base` → `components` → `utilities`; `layout.css` is added to this import list
   in Phase 3). Every HTML `<head>` links only `main.css`.

6. **`website/style-guide.html`** — a standalone page (not linked from `nav.json`, excluded from
   `build-search-index.js`'s crawl — Phase 1's script or this phase's update to it should skip
   files matching this name) rendering: the full color palette as swatches with their hex values
   labeled, the type scale, both buttons, a code block with a real Kotlin snippet, both callout
   variants, a table, badges, and a side-by-side sample of the brand-mark gradient vs. the UI
   accent-text gradient so the distinction from item 1 is visually obvious.

## Verification

- Open `style-guide.html` in a browser at 375px, 768px, and 1280px widths: confirm no horizontal
  scroll at any width, legible type at all sizes, and no layout breakage (this page has no
  header/sidebar chrome yet, so it's purely testing the token/component CSS in isolation).
- Contrast spot-check: verify `--text-body`-on-`--bg-base`, `--text-muted`-on-`--bg-base`, and
  `--text-muted`-on-`--bg-elevated` meet WCAG AA for normal body text (4.5:1) using any contrast
  checker (browser devtools' built-in contrast ratio display is sufficient) — this matters more
  than usual since dark-only means there's no light-mode fallback to lean on if a combination turns
  out too low-contrast.
- Paste a real Kotlin snippet from `README.md` (e.g. the `GameSave` quick-start example) into the
  style-guide's code block; confirm it renders with readable monospace type, correct padding, and
  (if any syntax coloring is added) doesn't clash with the dark background.
- Confirm the brand-mark gradient and the UI accent-text gradient are visually distinguishable side
  by side and that the documented usage rule (brand mark vs. UI chrome) is followed consistently
  within this file's own examples.
- Confirm `website/scripts/build-search-index.js` (from Phase 1) still runs clean and correctly
  excludes `style-guide.html` from its output.
