# Phase 005 — Content: Overview, Installation, Quick Start

Depends on: 001-004 (skeleton, styles, shell, and working JS — the Overview page's
contributor-avatars markup must match the DOM contract Phase 004 already defined, so this content
phase is sequenced after it rather than being truly parallel in practice, even though it touches
disjoint files from 006/007/008). See `000-overview.md` for the content-fidelity rule this phase
must follow.

## Scope

Author real narrative copy for the three front-door, non-grouped nav entries.

## Overview (`website/index.html`)

Must include, in this rough order:
1. A hero/intro stating what `Documents` is in one or two sentences (echo `README.md`'s tagline
   "Typed, reactive documents on top of MMKV — fast and a joy to write.").
2. **Required copy point:** explicitly frame the library as built "for elegancy" — echo
   `README.md`'s "Why this exists" section, specifically the line "Because libraries should be
   elegant — so the code people write with them can be elegant too. That's the entire point of
   this one." Do not just link to the README — write original prose making the same point in the
   site's own voice.
3. The problem statement, drawn from `docs/PRD.md` §1 (SharedPreferences/DataStore/SQLite/raw-MMKV
   comparison) — rewritten as prose, not a bulleted PRD dump.
4. The goals (`docs/PRD.md` §4, G1-G6) presented as a few prose paragraphs or a light feature list
   — but note G6 ("pluggable `Codec<T>`") is **stale/superseded** per ADR-0015 (the Codec
   abstraction was removed; serialization is a single internal CBOR format). Do not present G6 as
   current — either omit it or reframe it accurately as "serialization is handled internally via
   CBOR" without implying pluggability.
5. **Contributor avatars section** — a heading like "Built by" or "Contributors", containing the
   container markup Phase 004's `github-stats.js` populates (verify against Phase 004's actual
   implementation, don't guess the DOM contract).
6. Links out to Installation, Quick Start, and the Guide section.

## Installation (`website/installation.html`)

- The Maven Central coordinate and Gradle snippet from `README.md`'s "Install" section:
  `implementation("com.nomemmurrakh:documents:0.1.0")`.
- The "no setup ceremony" fact: MMKV initializes automatically, consumers never call
  `MMKV.initialize()` (source: `docs/api-design.md` §1, ADR-0012, ADR-0013 for iOS specifically).
- Platform support caveat up front: Android + iOS only (source: `README.md` platform table,
  `docs/architecture.md` §7) — link to the full Platform Support page (Phase 008) rather than
  duplicating the table here.
- A one-line mention that the library is on Maven Central (not JitPack — link this fact to ADR-0005
  if a "why" pointer feels useful, optional).

## Quick Start (`website/quick-start.html`)

- Reproduce, in original prose + code (not copy-pasted verbatim from the README, though the same
  `GameSave`/`Player` example is fine to reuse since it's the library's own canonical example), the
  walkthrough from `README.md`'s "Quick start" section: `@Serializable data class GameSave`,
  `Documents.document<GameSave>("slot-1")`, `set`, `update { }`, `get`, `delete`, `exists`.
- End with a "what's next" set of links into the Guide pages (Phase 006) for readers who want the
  full picture.

## Citation list (for review, not published on the site)

| Page | Claim | Source |
|---|---|---|
| Overview | Tagline, elegance framing | `README.md` lines 10, 56-71 |
| Overview | Problem statement | `docs/PRD.md` §1 |
| Overview | Goals G1-G6 | `docs/PRD.md` §4 (note G6 is stale, see ADR-0015) |
| Installation | Maven coordinate, Gradle snippet | `README.md` "Install" section |
| Installation | Auto MMKV init | `docs/api-design.md` §1; ADR-0012; ADR-0013 |
| Installation | Platform support | `README.md` platform table |
| Quick Start | `GameSave` walkthrough | `README.md` "Quick start" section |
| Quick Start | Method signatures (`get`/`set`/`update`/`delete`/`exists`) | `docs/api-design.md` §10 |

## Verification

- Fact-check pass: read each page side-by-side with `docs/PRD.md`, `README.md`, and
  `docs/api-design.md`; confirm no claim is invented and G6/Codec is not presented as current.
  Confirm nothing implies support for anything under PRD Non-goals (NG1-NG6) — e.g. Installation
  must not imply multi-process support works.
- Confirm every code snippet's method names/signatures exactly match `docs/api-design.md` §10 —
  `Documents.document<T>(key)`, `set(value)`, `update { current -> ... }`, `get()`, `delete()`,
  `exists()`.
- Load Overview with network on: confirm contributor avatars render for real. With network blocked
  (devtools): confirm the page still reads coherently with the avatars container hidden per Phase
  004's fallback contract — no broken layout, no dangling heading with nothing under it (if that
  looks awkward, consider hiding the whole "Built by" heading too when data is unavailable, and
  document that decision).
- Confirm all internal links (Overview → Installation → Quick Start → Guide) resolve to real files
  from Phase 001.
- Confirm the required "elegancy" framing is actually present as its own clearly readable statement
  on the Overview page, not buried or diluted.
