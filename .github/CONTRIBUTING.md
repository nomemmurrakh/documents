# Contributing to Documents

Thanks for your interest in contributing. This guide gets you productive quickly.

## Before you start

Read the design docs so your contribution fits the intended direction:

- [`docs/PRD.md`](../docs/PRD.md) — goals and **non-goals**.
- [`docs/api-design.md`](../docs/api-design.md) — the public surface (the source of truth).
- [`docs/architecture.md`](../docs/architecture.md) and [`docs/adr/`](../docs/adr) — how and why.

## Local setup

- JDK 17+
- Android SDK (for the `androidMain` / `androidTest` targets)
- Clone, then import into Android Studio / IntelliJ IDEA.

```bash
./gradlew build            # build all targets
./gradlew check            # run all tests + lint
./gradlew checkKotlinAbi   # verify public API hasn't changed unexpectedly
```

If you intentionally change the public API, run `./gradlew updateKotlinAbi` and commit the
updated API dump.

## Code style

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
  Import the Kotlin style guide into your IDE (`Settings → Editor → Code Style → Kotlin →
  Set from… → Kotlin style guide`).
- `explicitApi()` is **strict**: explicit visibility and types on all public declarations.
- Default to `internal`/`private`; only widen visibility when `api-design.md` requires it.
- KDoc every public entry point, with a usage example where it helps.
- No inline comments — prefer clear names and structure.

## Tests

- Add tests derived from [`docs/test-plan.md`](../docs/test-plan.md).
- Prefer `commonTest` against `InMemoryStorage`; use `androidTest` only for MMKV-specific
  behavior.
- New behavior without tests will not be merged.

## Pull requests

- Branch naming: `feat/…`, `fix/…`, `docs/…`, `chore/…`.
- Keep PRs focused — one logical change. Reference the related issue.
- Conventional Commit messages (`feat:`, `fix:`, `docs:`…) are appreciated.
- CI (build + tests + `checkKotlinAbi`) must pass.
- For anything touching the public API, explain the impact and the deprecation plan if it's
  a breaking change. Breaking changes land only in major releases.

## Good first issues

Look for the `good first issue` label. The tasks in [`docs/tasks.md`](../docs/tasks.md) are a
good map of self-contained units.

## Reporting bugs / proposing features

Use the issue templates. For open-ended discussion, prefer GitHub Discussions over issues.

By contributing, you agree your contributions are licensed under the Apache 2.0 License.
