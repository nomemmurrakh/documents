# 005 — Build, search index, and verification pass

**Depends on:** 001, 002, 003, 004 all implemented (not just planned). This is the closing
mechanical + editorial check, not a parallelizable content sub-plan — it runs once, last.

## Goal

Confirm the whole update is internally consistent, builds cleanly, and doesn't violate the
site's content-fidelity rule (from `docs/website-plans/000-overview.md`) before considering the
website update done.

## Steps

1. **Rebuild the site**: `node website/scripts/build.js` from the `website/` directory (or with
   the correct relative path from repo root — check `website/scripts/package.json`'s `build`
   script for the exact invocation used elsewhere). This regenerates every built HTML page from
   `website/src/**`, and will **fail outright** if `website/data/nav.json` references any href
   with no matching built page — the fastest signal that 001's nav entry and file, or 003's
   nav entry (if Option B was chosen), are mismatched.
2. **Rebuild the search index**: `node website/scripts/build-search-index.js`. Confirms the new
   Guide page (and any new Use Case page) gets indexed under its own `<h1>`/`<h2>` sections —
   spot check `website/data/search-index.json` contains entries with `href` pointing at
   `/guide/field-decorators.html`.
3. **Run link-check**: `node website/scripts/link-check.js`. Must print
   `Checked N pages, all links resolve.` with zero broken links. If it fails, the printed
   `Broken link in <file>: <ref>` lines point at exactly which sub-plan's output to fix.
4. **Content-fidelity spot check** (manual, per `000-overview.md`'s human-applied review
   convention — there is no automated fact-checker):
   - Confirm no page anywhere states or implies that `Documents` ships encryption *today* — every
     encryption mention must be phrased as "you can build this yourself using
     `FieldDecorator`," never "Documents encrypts your data" or similar. Do not, however, require
     pages to disclaim encryption as a permanent non-goal — PRD NG2 frames built-in encryption at
     rest as deferred ("surface later"), not ruled out, and the site should not contradict that by
     implying it never will exist.
   - Confirm every code sample's API usage (`FieldDecorator`, `decorators` on
     `DocumentConfig`/`CollectionConfig`, `Collection.document(key) { }`) matches the actual
     shipped API in `documents/src/commonMain/kotlin/com/nomemmurrakh/documents/` as of commit
     `cc60029` — re-read the source at verification time, don't trust this plan's transcription
     of it in case of drift.
5. **`Note` convention check**: confirm every non-Use-Case, non-`sample-app.html` snippet
   introduced by 001/002 uses the `Note` domain object, and that whatever 003 produced (Option A
   or B) introduces its domain example consistently with how other Use Case pages do it (own
   realistic type, not `Note`).
6. **Nav sanity read**: open `website/data/nav.json` and confirm the Guide section reads
   sensibly top-to-bottom with the new entry in place, and (if Option B from 003 was chosen)
   the Use Cases section likewise.

## Acceptance

- All three scripts (`build.js`, `build-search-index.js`, `link-check.js`) exit 0.
- The content-fidelity and API-accuracy spot checks in step 4 are explicitly confirmed, not
  assumed — call out any discrepancy found rather than silently fixing and moving on, since a
  discrepancy at this stage likely means one of the earlier sub-plans drifted from the real API
  and should be corrected at the source (the relevant page), not patched here.
- No `docs/website-plans/000-overview.md` "deferred" items (versioning, i18n, analytics) are
  touched — this sub-plan is verification-only, strictly scoped to the FieldDecorator update.
