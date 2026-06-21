# ADR-0014: On-device benchmarks, platform-native harnesses

**Status:** Accepted
**Date:** 2026-06-21

## Context

`docs/tasks.md` T9.1 asks to microbenchmark `Documents` write/read **vs raw MMKV** and record the
results in the README. The natural KMP benchmarking tool is `kotlinx-benchmark` (JMH on the JVM
host), but it cannot satisfy "vs raw MMKV": MMKV is not a plain-JVM dependency. On Android it needs
a real `Context` and an mmap-backed file, so it only runs on a device/emulator (the project's own
`MmkvStorageTest` is already an instrumented device test, not a JVM-host test). On Apple, MMKV ships
as a CocoaPod and runs on the simulator/device (ADR-0013). There is no single cross-platform
on-device microbenchmark tool that drives both platforms against a real MMKV.

CI has no device attached, and ADR-0005 already constrains Apple builds to a macOS host. So a true
Documents-vs-MMKV comparison must run on hardware, locally, outside the CI `check` path.

The thing worth measuring is the overhead `Documents` adds over a hand-rolled MMKV usage: field
decomposition into `{doc}::{field}` keys plus per-field JSON encode/decode, versus encoding the same
value once and calling MMKV's byte API directly.

## Decision

Use **one on-device timing harness shared across both platforms**, run by the developer, with
results pasted into the README. Benchmarks live in the existing test source sets and add **no public
API** — the published common klib ABI does not move (`checkLegacyAbi` stays green).

The harness is a `kotlin.time.TimeSource.Monotonic` loop (warmup + N measured iterations, reporting
median and p95 ns/op): on Android an `androidDeviceTest` instrumented `@Test` logging under the
`BENCH` tag; on iOS an `iosTest` `@Test` printing to test output. It is less rigorous than JMH (no
process isolation, GC/ARC noise) and the numbers are a README ballpark, stated as such.

**Jetpack Microbenchmark was tried first on Android and rejected.** `androidx.benchmark` aborts
against a KMP library's instrumented test with `ERRORS: ACTIVITY-MISSING DEBUGGABLE` — it requires a
non-debuggable build variant and a launchable Activity, i.e. a dedicated benchmark module / build
type. That is heavier than this optional-for-first-tag task warrants, so the shared `TimeSource`
harness is used on both platforms instead (which also makes the two platforms methodologically
symmetric). XCTest's `measure {}` is likewise unreachable from Kotlin/Native test code, so iOS would
need a hand-rolled loop regardless.

- **Baseline = raw MMKV.** For each case, the Documents call is compared against encoding the same
  5-field value with the same `Json` and calling MMKV's byte API directly (Android
  `encode`/`decodeString`; iOS `setData:`/`getDataForKey:`). This isolates Documents' decomposition +
  per-field codec cost.
- **Cases:** `set(REPLACE)` of a 5-field doc, `get`, `set(UPDATE){ one field }`, `delete` (measures
  set + delete, since the loop does not pause timing around setup), and a single-key field-delegate
  write.
- **Isolation from existing device tests.** `connectedAndroidDeviceTest` runs the whole
  `androidDeviceTest` set, which drags in the JVM-host `commonTest` flow tests (they crash the
  instrumentation process on-device). The benchmark is therefore run with a class filter
  (`-Pandroid.testInstrumentationRunnerArguments.class=...DocumentsBenchmark`) so only it runs on the
  device; `MmkvStorageTest`'s runner is untouched.
- **Not in CI / not in `check`.** Benchmarks run only on a connected device/simulator, on request,
  and against a single ADB transport (two transports to one device crash the run).

## Consequences

**Positive**
- Delivers the real "vs raw MMKV" number T9.1 asks for, on both shipping platforms, on the hardware
  consumers actually run.
- No public surface change; benchmarks are test-source-only, so `checkLegacyAbi` is unaffected and no
  `updateLegacyAbi` is needed. No new production dependency (Jetpack was dropped).
- One shared harness, so Android and iOS are measured the same way and the tables are comparable.

**Negative / cost**
- The harness is hand-rolled and less statistically rigorous than JMH/Jetpack (no process isolation;
  GC/ARC noise; the Android run is a debuggable build, so absolute numbers are inflated). The numbers
  are illustrative for relative comparison, not authoritative.
- Running requires manual care: a single ADB transport and a class filter, or the run crashes / pulls
  in unrelated JVM-host tests.
- Numbers are device-specific; the README table is labelled with the device/OS it was captured on
  and is not asserted in CI, so it can drift if not refreshed.

## Alternatives considered

- **`kotlinx-benchmark` / JMH on the JVM host** — the standard KMP choice and CI-friendly, but it
  cannot exercise a real MMKV without a device, so it could only benchmark Documents over
  `InMemoryStorage`. Rejected: it does not answer "vs raw MMKV".
- **Jetpack Microbenchmark on Android** — the platform's rigorous tool (warmup/isolation/allocation
  stats), and the first choice here. Rejected after trying: it aborts a KMP library's debuggable
  instrumented test with `ERRORS: ACTIVITY-MISSING DEBUGGABLE`, requiring a non-debuggable benchmark
  build variant + a launchable Activity (a dedicated module/build type). Too heavy for an
  optional-for-first-tag task; revisit if rigorous Android numbers are later needed.
- **Defer Phase 9 to v1.x** — T9.1 is explicitly optional for the first tag. Rejected: the benchmark
  was requested now and the developer has both devices on hand.

## Notes

Phase 9 / T9.1 of `docs/tasks.md`. The README's `vs DataStore / SharedPreferences` comparison stays
a future v1.x item (`docs/roadmap.md`); this ADR covers only the vs-raw-MMKV baseline. The Android
benchmark runs via `connectedAndroidDeviceTest` (device attached); the iOS benchmark via
`iosSimulatorArm64Test` (simulator) or a device run. Both honor the cinterop quirks from ADR-0013.
