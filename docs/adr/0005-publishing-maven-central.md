# ADR-0005: Publish to Maven Central via macOS CI, not JitPack

**Status:** Accepted
**Date:** 2026-06-15

## Context

`Documents` is a Kotlin Multiplatform library with Apple targets (`iosArm64`,
`iosSimulatorArm64`). Prior Nomem Labs libraries (`chains`, `SwipeDecoration`) ship
via JitPack, so JitPack was the natural first choice here too.

JitPack builds exclusively on Linux. Apple/native targets can only be compiled on a macOS
host with Xcode. Therefore a full KMP publish on JitPack fails on the iOS klibs — there is no
workaround within JitPack.

A KMP publication is also not a single artifact: the Kotlin plugin emits one Maven
publication per target plus an umbrella `kotlinMultiplatform` root publication, tied together
by Gradle module metadata that lets a consumer resolve the correct klib for their target.

## Decision

Publish to **Maven Central**, building on a **`macos-latest` CI runner** (macOS cross-compiles
all targets — Android, JVM, and Apple — whereas Linux cannot produce Apple klibs). Use the
`com.vanniktech.maven.publish` Gradle plugin to configure the per-target publications,
sources/javadoc artifacts, GPG signing, and Central Portal upload.

JitPack is **not** used for the multiplatform artifact. Plain `maven-publish` is retained only
for `publishToMavenLocal` as a fast local-testing loop.

## Consequences

**Positive**
- iOS klibs are actually produced and published; consumers on any target resolve correctly.
- The Vanniktech plugin avoids the well-known raw-`maven-publish` failure mode where KMP
  publications split into multiple Central staging repositories.
- One macOS publish job keeps the pipeline simple (no cross-OS artifact merging).

**Negative / cost**
- macOS CI minutes cost more than Linux.
- Higher one-time setup than JitPack: a verified Central Portal namespace and GPG signing
  keys are required (a manual human prerequisite, not automatable by the build).
- Diverges from the existing JitPack-based release habit for Nomem Labs libraries.

## Alternatives considered

- **JitPack (Android/JVM-only artifact)** — would restore the "just tag a release" simplicity,
  but only by dropping the Apple targets, which defeats the library being KMP. Rejected.
- **Split CI: Android on Linux, iOS on macOS, two publish jobs** — viable but introduces
  multi-repo / artifact-merging complexity for no real benefit at this scale. Rejected in
  favor of a single macOS job.
- **GitHub Packages** — works for KMP but is awkward for public consumption (consumers need
  auth even for reads). Rejected for a public library.

## Notes

Revisit if JitPack adds macOS build support, or if the project later wants SNAPSHOT
publishing (which has lighter requirements than a full Central release).
