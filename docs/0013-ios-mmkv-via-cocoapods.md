# ADR-0013: Bind MMKV on iOS via the Kotlin CocoaPods plugin

**Status:** Accepted
**Date:** 2026-06-21

## Context

`Documents` targets `iosArm64` and `iosSimulatorArm64`, but the iOS `Storage` is unbuilt:
`PlatformStorage.ios.kt` still throws "not yet supported on iOS" and `ensureInitialized()` is a
no-op — the contract-only posture left by ADR-0012's follow-up. Implementing the iOS side requires
calling MMKV's native API from Kotlin/Native.

MMKV does not ship as a Maven/klib artifact for Apple. The Android dependency
(`com.tencent:mmkv`) is JVM-only; on Apple, MMKV is distributed as a **CocoaPod** (an
Objective-C framework). This mirrors ADR-0005's constraint that Apple targets can only be built on
a macOS host — here the dependency itself is Apple-toolchain-bound.

So the iOS target needs MMKV's Objective-C surface (`mmkvWithID:`, `getDataForKey:` /
`setData:forKey:`, `removeValueForKey:`, `containsKey:`, `allKeys`, `initializeMMKV:`) bound into
Kotlin/Native as a cinterop klib.

iOS also differs from Android at init time (ADR-0012): Android receives a `Context` from
`androidx.startup`; iOS has no Context and discovers its own sandbox path in-process. The consumer
contract stays identical (`Documents.create("app")` and nothing else) — only the mechanism differs.

## Decision

Use the **Kotlin CocoaPods plugin** (`org.jetbrains.kotlin.native.cocoapods`) to consume MMKV on
iOS. Declare the pod in `documents/build.gradle.kts`:

```kotlin
kotlin {
    cocoapods {
        ios.deploymentTarget = "12.0"
        pod("MMKV") { version = libs.versions.mmkv.get() }
    }
}
```

The plugin generates the cinterop bindings; the iOS `MmkvStorage` calls the generated
`cocoapods.MMKV.*` declarations. iOS's `ensureInitialized()` actual becomes a once-guarded call to
`MMKV.initializeMMKV(rootDir)` using the in-process app sandbox path (e.g. under
`NSDocumentDirectory`/Library), so the zero-touch consumer contract from ADR-0012 is preserved with
no `Context` and no consumer-visible init call.

The iOS `Storage` implementation is `internal`, and the common companion (`create`/`inMemory`) is
unchanged, so the published common klib ABI does not move (`checkLegacyAbi` stays green).

## Consequences

**Positive**
- iOS gets a real MMKV-backed `Storage`, matching the Android `MmkvStorage` byte-for-byte against
  the `Storage` contract; the cross-platform consumer experience is finally symmetric.
- The CocoaPods plugin auto-generates and maintains the cinterop, so we do not hand-write or vendor
  a framework.
- No public surface change — this is an internal/iOS-platform detail, like ADR-0012.

**Negative / cost**
- Building and publishing the iOS target now requires the CocoaPods toolchain (Ruby + `pod`) on the
  macOS host, in addition to Xcode. This is consistent with ADR-0005 (Apple artifacts already build
  only on a `macos-latest` runner), but adds a setup step to that runner.
- A generated podspec / pod integration appears in the build; local iOS builds need `pod install`
  to have run (the plugin drives this).
- **Consumer integration:** a consumer who pulls the published klib must have MMKV available to the
  linker via their own CocoaPods (or SPM) setup. This must be documented in the README's iOS
  install section.

## Alternatives considered

- **Hand-written `.def` cinterop** — gives full control and drops the CocoaPods plugin, but then we
  own the framework artifact, header paths, and version bumps by hand. Rejected for higher ongoing
  maintenance versus the plugin's generated bindings.
- **Swift Package Manager binding** — KMP's SPM-export tooling is less mature than its CocoaPods
  support for *consuming* a native dependency. Rejected for now; revisitable if MMKV's SPM support
  and KMP's SPM consumption both mature.
- **Leave iOS unsupported (status quo)** — keeps `platformStorage` throwing. Rejected: it
  contradicts the decision to ship iOS in v1.0 and leaves ADR-0012's follow-up permanently open.

## Notes

Resolves the **Follow-up** in [ADR-0012](0012-automatic-mmkv-initialization.md): the iOS
`ensureInitialized()` and `platformStorage` are implemented under Phase 10 of `docs/tasks.md`.
Revisit the binding choice if KMP's SPM consumption matures or if MMKV changes its Apple
distribution.
