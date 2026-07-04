# CLAUDE.md

Guidance for Claude Code working in this repository.

## What this is

`Documents` — a document-oriented, typed, reactive Kotlin Multiplatform library backed by
MMKV. Read these before writing code, in order:

1. `docs/PRD.md` — why it exists, goals, and **non-goals** (do not build non-goals).
2. `docs/api-design.md` — the exact public surface. This is the source of truth.
3. `docs/architecture.md` — how the layers fit; the decisions log.
4. `docs/adr/` — why specific decisions were made.
5. `docs/tasks.md` — ordered build plan. Work tasks top to bottom.
6. `docs/test-plan.md` — write tests from here, against intent.

## Working rules

- **Never build or run** (Gradle, tests, samples) **without explicit approval.** Propose the
  command and wait.
- **No comments in code.** Names and structure carry intent.
- Work **one task at a time** from `docs/tasks.md`. Each task must compile and have tests
  before moving to the next.
- Do not implement anything listed under **Non-goals** in the PRD or under "Later/maybe" in
  the roadmap.
- If a needed decision isn't in the docs, **ask** rather than inventing an architecture.
  If you do make a call, record it as a new ADR in `docs/adr/`.

## Conventions

- Kotlin official coding conventions. `explicitApi()` is **strict** — every public
  declaration needs an explicit visibility modifier and explicit types.
- All API and logic live in `commonMain`. Only `Storage` implementations are
  platform-specific (`androidMain`, etc.).
- Public entry points require KDoc.
- Prefer `internal`/`private` by default; expand the public surface only when `api-design.md`
  calls for it.

## Build & checks (run only with approval)

- Build: `./gradlew build`
- Tests: `./gradlew check`
- API governance: `./gradlew checkKotlinAbi` (Kotlin built-in ABI validation);
  regenerate the reference dump with `./gradlew updateKotlinAbi`.

## Definition of done for a task

Compiles, has tests derived from `docs/test-plan.md`, passes `checkKotlinAbi`, public surface
matches `docs/api-design.md`, no comments, KDoc on new public entry points.
