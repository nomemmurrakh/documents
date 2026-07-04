# ADR-0020: Align Kotlin package with Maven groupId (`com.nomemmurrakh`)

**Status:** Accepted
**Date:** 2026-07-03

## Context

ADR-0005 and task T0.3a fixed the Maven publishing coordinates as `com.nomemmurrakh:documents`
when the vanniktech publishing plugin was wired up. `group = "com.nomemmurrakh"` in
`documents/build.gradle.kts`, and the install snippet in `README.md`
(`implementation("com.nomemmurrakh:documents:0.1.0")`), have reflected that groupId ever since.

The Kotlin package the library actually ships, however, was never revisited to match: every
source file declared `package io.github.nomemmurrakh.documents`, and the Android `namespace`
(both the library's and the sample's `applicationId`) mirrored it. A consumer who added the
Maven dependency and then typed the import got
`import io.github.nomemmurrakh.documents.Documents` — a package that doesn't match the artifact
coordinate they just declared. This split was never a deliberate two-namespace design; the
package name simply wasn't reconsidered after the groupId was finalized.

## Decision

Rename the Kotlin package from `io.github.nomemmurrakh.documents` to `com.nomemmurrakh.documents`
across all source sets (`commonMain`, `commonTest`, `androidMain`, `androidDeviceTest`,
`androidHostTest`, `iosMain`, `iosTest`), the sample app, the `androidx.startup` initializer
reference in `AndroidManifest.xml`, and the Android `namespace`/`applicationId` in both
`documents/build.gradle.kts` and `sample/build.gradle.kts`, so package, namespace, and groupId
are all `com.nomemmurrakh`. `group` and the `mavenPublishing { coordinates(...) }` block are
unchanged — they were already correct.

## Consequences

**Positive**
- The import statement a consumer writes now matches the Maven coordinate they depend on.
- Package, Android namespace, and Maven groupId are one consistent identity instead of two.

**Negative / cost**
- **Binary-breaking change**: every public symbol's fully-qualified name changes, so the ABI
  baseline (`documents/api/documents.klib.api`) must be regenerated via `./gradlew updateLegacyAbi`.
  Acceptable now because the library is still pre-1.0 (`0.1.0`) and not yet published to Maven
  Central — there are no real consumers to break.

## Alternatives considered

- **Change the groupId to match the package (`io.github.nomemmurrakh`) instead.** Rejected: the
  groupId was the deliberate, documented choice (ADR-0005/T0.3a); the package name was the
  unreviewed leftover.
- **Leave both as-is.** Rejected: cheapest possible time to fix the mismatch is before any
  release reaches Maven Central; waiting turns this into a breaking change for real consumers.
