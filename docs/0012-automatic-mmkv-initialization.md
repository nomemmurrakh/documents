# ADR-0012: Documents owns MMKV initialization (zero-touch auto-init)

**Status:** Accepted
**Date:** 2026-06-21

## Context

`Documents` wraps MMKV, but MMKV requires a one-time `MMKV.initialize(...)` per process before any
store is opened. Until now the **consumer** made that call themselves — the sample's `SampleApp`
ran `MMKV.initialize(this)` in `Application.onCreate`. That leaks the wrapped MMKV dependency
straight through the library's surface: a user of a *Documents* library should never have to know
MMKV exists, let alone bootstrap it.

This forced a distinction between two kinds of configuration that had been conflated:

- **Per-store tunables** — `json`, `multiProcess`, `dispatcher`. These can legitimately differ
  between stores and already live correctly in the `Documents.create(name) { }` block via
  `DocumentsConfig`. **Not touched by this ADR.**
- **Process-wide one-time bootstrap** — `MMKV.initialize`. Must run exactly once before *any*
  store. On Android it needs a `Context` that `create(name)` deliberately does not take (to keep
  the common companion signature platform-agnostic). This does **not** belong in `create`'s config
  block, and is what this ADR addresses.

The platforms are asymmetric underneath: Android needs a `Context` delivered from outside; iOS
needs nothing external (the sandbox path is discoverable in-process). iOS MMKV is also **not wired
yet** — `PlatformStorage.ios.kt` still throws "not yet supported on iOS".

This change is not a task in `docs/tasks.md`; it is a user-initiated improvement. Per the repo
working rules ("if you make a call, record it as an ADR"), it is recorded here.

## Decision

**Zero-touch auto-init on both platforms.** The consumer writes only `Documents.create("app")`
and never references MMKV or a `Context`. The shared consumer-facing contract is identical across
platforms; only the internal mechanism differs.

- A common `internal expect fun ensureInitialized()` is called at the top of `Documents.create`
  (before `platformStorage(...)`). `inMemory()` does **not** call it — no MMKV is involved.
- **Android:** `ensureInitialized()` is a no-op `actual`, because a `public class
  DocumentsInitializer : androidx.startup.Initializer<Unit>` has already run `MMKV.initialize` at
  process start. It is registered in `androidMain`'s `AndroidManifest.xml` under androidx.startup's
  `InitializationProvider` (manifest-merged into the consuming app). `DocumentsInitializer` is
  `public` only because androidx.startup instantiates it reflectively; it is framework-invoked, not
  a user entry point (so it carries KDoc saying so, and no `Documents.initialize` is exposed).
- **iOS:** `ensureInitialized()` is a no-op `actual` for now — it establishes the contract and is
  the slot where lazy `MMKV.initialize(rootDir)` will live once iOS MMKV is wired. `platformStorage`
  on iOS is unchanged (still "not yet supported").

No new **public** entry point is added (`create`/`inMemory` are unchanged; `ensureInitialized` is
`internal`), so the published common klib ABI does not move. The Android-only `DocumentsInitializer`
is not part of the common surface.

## Consequences

**Positive**
- The library fully owns MMKV; consumers never touch it. The sample's `SampleApp` and its manual
  `MMKV.initialize` are deleted, demonstrating the zero-touch experience.
- Android and iOS share one consumer contract (`Documents.create` and nothing else).
- Public ABI is unchanged; this is an internal/Android-platform detail.

**Negative / cost**
- Adds an `androidx.startup` dependency to the Android artifact.
- iOS auto-init is contract-only until iOS MMKV lands — see follow-up.
- `api-design.md` §1 gains a note (init is automatic) so the source of truth stays accurate.

## Follow-up

When iOS MMKV is wired (its own future task), implement the iOS `ensureInitialized()` actual to
lazily call `MMKV.initialize(rootDir:)` using the in-process sandbox path, and replace the iOS
`platformStorage` "not yet supported" body with a real `MmkvStorage`.

**Resolved by [ADR-0013](0013-ios-mmkv-via-cocoapods.md) and Phase 10 of `docs/tasks.md`** — MMKV
is bound on iOS via the Kotlin CocoaPods plugin; `ensureInitialized()` calls
`MMKV.initializeMMKV(rootDir)` once and `platformStorage` returns a real iOS `MmkvStorage`.

## Alternatives considered

- **Explicit `Documents.initialize()` on both platforms** — symmetric in shape, but iOS does not
  need it and Android needs a `Context`, so the signatures would still diverge; it also adds a
  public entry point and a call the user can forget. Rejected in favor of zero-touch.
- **`context` parameter on `create(name, context)`** — diverges `create`'s signature per platform
  and threads `Context` awkwardly through the common companion. Rejected.
- **Leave init to the consumer (status quo)** — the exact awkwardness that prompted this. Rejected.
