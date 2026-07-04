# Phase 010 — Final Polish & Review

Depends on: 001-009 (everything must exist and have passed QA first).

## Scope

The closing phase: a holistic editorial pass across all ~25 pages (voice/tone can drift since
Phases 005-008 were written somewhat independently), a final technical fact-check spot-check
sweep, a durable `website/README.md`, an explicit "deployment is future work" note, and a final
go/no-go read-through before calling the docs-site project done. This phase does **not** deploy
anything — deployment remains a separate, later discussion per the project's original scope.

## Deliverables

1. **Editorial consistency pass** — read every page in nav order (Overview → Installation → Quick
   Start → Guide's 6 pages → Use Cases' 5 pages → Concepts' 5 pages → Platform Support →
   Benchmarks → Sample App → Design Decisions → Contributing) checking for: consistent voice/tone
   (the site should read as one coherent product, not four differently-toned content batches),
   consistent terminology (e.g. always "document" per ADR-0004, never "record"/"store" used
   interchangeably for the same concept), no leftover placeholder/lorem-ipsum/"Content pending"
   text anywhere, no orphaned links or dead-end pages missing a next-step link.

2. **Final fact-check spot-check** — re-verify a sample of claims across the whole site against
   source docs one more time (not a full re-audit of every phase's own citation list, which already
   happened per-phase, but a fresh cross-cutting sanity check specifically looking for
   drift/contradiction *between* pages written in different phases — e.g. does the Concurrency
   page's description of `update()` match Read & Write's description exactly; does Session & User
   State's collection-deletion pattern match what Collections & Testing actually documents).

3. **`/api/` link final check** — confirm the header's "API Reference ↗" link still points at the
   documented `/api/` path constant from `000-overview.md`, and that it's implemented as a single
   value/constant (not hardcoded in multiple places), so a future deployment step only needs to
   update it in one location once Dokka's output has a real home.

4. **`website/README.md`** finalized as the durable project doc: how to build (`node
   scripts/build.js`), how to rebuild the search index (`node scripts/build-search-index.js`), how
   to preview locally (the documented static-server command from Phase 001), how to add a new page
   (add an HTML source + a `nav.json` entry, re-run the build), how to regenerate brand assets if
   `brand/` changes (the exact `rsvg-convert`/`png-to-ico` commands from Phase 001).

5. **"Deployment (future work)" note** — a short, explicit section (in `website/README.md` and/or
   `plans/000-overview.md`) stating: this site is complete and locally previewable; hosting
   (GitHub Pages/Netlify/Vercel/other), CI wiring to auto-build on push, a custom domain, and making
   the `/api/` Dokka link resolve to a real deployed Dokka build are all deferred to a separate,
   later discussion — this project's scope ends here, deliberately.

6. **Update `plans/000-overview.md`'s status table** — mark all 10 phases "Done."

## Verification

- Full read-through of every page in nav order — confirm no placeholder text, no voice
  inconsistency that reads jarringly, no dead-end pages.
- Grep every shipped HTML file for `TODO`/`FIXME`/"Content pending"/"lorem ipsum" — must return
  zero hits.
- Confirm the site, opened via the documented local static server (or a `file://` path, checking
  which parts still work without a server — same-origin `fetch` calls for search/nav will require
  the static server, not raw `file://`, so document that constraint clearly rather than promising
  full `file://` functionality if it doesn't hold) with no network at all, degrades gracefully:
  search still works (index is local), only the GitHub star count/avatars gracefully hide per
  Phase 004's fallback contract.
- Final stakeholder review checkpoint: present the locally-running site to the project owner for
  sign-off before considering the docs-site project "done." Deployment discussion begins only
  after this sign-off, as a separate initiative.
