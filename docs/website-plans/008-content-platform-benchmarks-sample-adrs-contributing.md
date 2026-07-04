# Phase 008 — Content: Platform Support, Benchmarks, Sample App, Design Decisions, Contributing

Depends on: 001-004. Independent of 005/006/007 content-wise.

## Scope

Author the remaining five nav entries — each short and reference-style rather than deep narrative.

## `website/platform-support.html`

The Android/iOS support table, reproduced accurately: Android ✅ (MMKV), iOS `arm64` +
`simulatorArm64` ✅ (MMKV via CocoaPods). One public API across both, living entirely in
`commonMain` (`docs/architecture.md` §1, §7). No JVM/desktop/wasm target. Sources: `README.md`
platform table; `docs/architecture.md` §7.

## `website/benchmarks.html`

Reproduce the real iOS benchmarks table from `README.md` **exactly** (same operations, same
µs/ns figures, same device/iteration-count footnote: iPhone 17 Pro simulator, iOS 26.1, median of
20k iterations, CBOR encoding) — do not round, alter, or "improve" the numbers. Preserve the
methodology framing from ADR-0014: on-device harness (not JMH/`kotlinx-benchmark`), not run in CI,
device-/OS-specific, illustrative rather than authoritative, each raw-MMKV baseline does the exact
same underlying work by hand (same field keys, same per-field CBOR calls) so the comparison
isolates the abstraction's cost rather than comparing decomposed-fields vs. single-blob (an unfair
comparison). **Android section must say "pending re-run"** exactly as `README.md` and ADR-0014
state — an instrumented harness exists (`connectedAndroidDeviceTest`) but current numbers predate
the CBOR switch and need a fresh run. Do not fabricate or estimate Android numbers. Sources:
`README.md` Benchmarks section; ADR-0014.

## `website/sample-app.html`

Walk through the real `sample/` Compose app (package `com.nomemmurrakh.documents.sample`,
`MainActivity.kt`'s `DemoScreen`), describing only what the code actually does:
- A default-store document `Documents.document<GameSave>("slot-1")` with a "Reset (set value)"
  button (full replace via `set`), a "Level +1 (update)" button and "Coins +50" button (both via
  `update { current -> current.copy(...) }`), a "Player hp -10" button (touches the nested
  `Player` sub-blob), and a "delete()" button.
- A live `get()`/`exists()` readout plus a separate `fieldFlow(GameSave::coins, default = 0)`
  readout proving per-field reactivity independent of the whole-document flow.
- A separate `Documents.collection("cache")` holding a `Draft` document, with "Edit draft" and
  "Clear draft" buttons, demonstrating that editing the draft never affects the `slot-1` readout —
  proving collection file isolation.
- Link/pointer to clone the repo and run the sample (`sample/` module, Android only) — no
  deployment of the sample itself is in scope for this site.
Source: `sample/src/main/kotlin/com/nomemmurrakh/documents/sample/MainActivity.kt` (verified
against the actual file — every described button/behavior must exist in that file, nothing
invented); `README.md` "Try the sample" section.

## `website/design-decisions/index.html`

A curated, grouped index — **not all 20 ADRs reproduced as site pages.** Group by theme, each
group a short list of one-line "why it matters" summaries linking out to the real
`docs/adr/000N-*.md` files on GitHub (decide once: link to GitHub's rendered file view, e.g.
`https://github.com/nomemmurrakh/documents/blob/master/docs/adr/0001-field-decomposition.md`,
which is the more useful reading experience than a raw blob URL). Suggested grouping (adjust if a
cleaner grouping emerges while writing, but keep all 20 accounted for):
- **API shape** — ADR-0004 (vocabulary), ADR-0007 (superseded by ADR-0016), ADR-0008 (update
  returns copy), ADR-0016 (Documents entry point + Collection), ADR-0017 (drop MergeStrategy),
  ADR-0018 (update verb + single-field update).
- **Storage & serialization** — ADR-0001 (field decomposition), ADR-0003 (SerialDescriptor
  walking), ADR-0006 (superseded by ADR-0015), ADR-0015 (CBOR internal format).
- **Reactivity & concurrency** — ADR-0002 (SharedFlow change bus), ADR-0011 (synchronous API,
  non-suspend lock).
- **Platform & publishing** — ADR-0005 (Maven Central via macOS CI), ADR-0012 (auto MMKV init),
  ADR-0013 (iOS MMKV via CocoaPods), ADR-0019 (drop multiProcess), ADR-0020 (package aligned with
  groupId).
- **Errors & testing** — ADR-0009 (DocumentDecodingException), ADR-0010 (field delegate
  serializer), ADR-0014 (on-device benchmarks).
Superseded ADRs (0006, 0007) must be clearly labeled "Superseded by ADR-00XX" in their one-line
summary, never presented as a current decision. Source: `docs/adr/README.md` (the authoritative
index table — cross-check every grouping/status against it).

## `website/contributing.html`

Encourage contribution: link to the repo, `LICENSE` (confirm exact license by reading the file,
currently Apache 2.0), and the real CI gates a PR must pass, matching
`.github/workflows/gradle.yml` exactly: a build+test matrix job (`iosSimulatorArm64Test` on
macOS, `testAndroidHostTest` on Ubuntu), an `abi-check` job (`:documents:checkKotlinAbi`), and a
`detekt` job. Do not describe checks that don't exist in that workflow file (no mention of a docs
build check, since none exists in CI). Mention `docs/CONTRIBUTING.md`/`.github/CONTRIBUTING.md` if
it exists as the canonical detailed guide, with this page as a friendlier on-ramp.

## Verification

- Benchmarks: spot-diff the reproduced iOS table's every cell against `README.md`'s actual table
  — must match exactly. Confirm Android is stated as "pending"/"not yet re-run," never given
  numbers.
- Sample App: re-read `MainActivity.kt` line by line against this page's description; every button
  label, every described interaction, and the "editing the draft never changes slot-1" claim must
  match the real code exactly.
- Design Decisions: confirm links resolve to real files (`docs/adr/0001` through `0020`, cross-
  checked against `docs/adr/README.md`'s table) and that ADR-0006 and ADR-0007 are labeled
  superseded, not presented as current.
- Contributing: confirm the stated CI jobs match `.github/workflows/gradle.yml`'s actual job names
  and commands exactly (`iosSimulatorArm64Test`, `testAndroidHostTest`, `:documents:checkKotlinAbi`,
  `detekt`).
- Platform Support: confirm iOS targets are named correctly (`arm64` + `simulatorArm64`, not a
  generic "iOS" claim that could imply x86_64/Intel simulator support that isn't listed).
