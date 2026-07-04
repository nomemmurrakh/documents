# Documents — Task Breakdown (v1)

**Last updated:** 2026-06-15

Ordered, independently testable units. Build top to bottom; each task should compile and
have tests before moving on. This is also the source for `good first issue` labels later.

> Workflow reminder for the agent: do not build or run without approval; no comments in code.

---

## Phase 0 — Project scaffolding

- [ ] **T0.1** KMP library module: `commonMain`, `androidMain`, `commonTest`, `androidTest`
      source sets. Kotlin + `org.jetbrains.kotlin.plugin.serialization`.
- [ ] **T0.2** Enable `explicitApi()` strict. Add `binary-compatibility-validator`.
- [ ] **T0.3** Publishing setup — **Maven Central, not JitPack** (JitPack builds on Linux
      only and cannot compile Apple klibs; see [ADR-0005](adr/0005-publishing-maven-central.md)). Sub-tasks:
  - [ ] **T0.3a** Apply `com.vanniktech.maven.publish`. Configure coordinates
        (`groupId = "com.nomemmurrakh"`, `artifactId = "documents"`, `version`), POM metadata
        (name, description, url, license, developer, scm), and GPG signing from an in-memory
        key supplied via CI secrets.
  - [ ] **T0.3b** Add `.github/workflows/publish.yml`, triggered on release tag, running on
        `macos-latest` (builds all targets — Android, JVM, **and** Apple), calling
        `./gradlew publishAllPublicationsToMavenCentral`.
  - [ ] **T0.3c** Keep plain `maven-publish` only for `publishToMavenLocal` (fast local test
        loop). Do **not** target JitPack for the multiplatform artifact.
  - [ ] **T0.3d** *(human prerequisite, not Claude Code)* Verify the Central Portal namespace
        and add `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`,
        `SIGNING_KEY_PASSWORD` as repo secrets before the first publish.

## Phase 1 — Storage SPI

- [ ] **T1.1** Define `internal interface Storage` (getBytes/putBytes/remove/contains/keys).
- [ ] **T1.2** `InMemoryStorage` in `commonMain` (concurrency-guarded map). Tests.
- [ ] **T1.3** `MmkvStorage` in `androidMain` wrapping a named MMKV instance.

## Phase 2 — Key scheme

- [ ] **T2.1** Key construction `{doc}::{field}`; reject `::` in document keys. Pure-function
      tests for round-trip and rejection.

## Phase 3 — Codec layer

- [ ] **T3.1** `interface Codec<T>` (encode/decode).
- [ ] **T3.2** `KotlinxCodec` default, serializing individual field values. Tests for
      primitives, enums, nullable, and a nested `@Serializable` sub-blob.

## Phase 4 — Decomposition (hardest)

- [ ] **T4.1** Custom `CompositeEncoder` writing each element to `{doc}::{field}` via
      `SerialDescriptor`. Tests round-tripping a multi-field data class.
- [ ] **T4.2** Custom `CompositeDecoder` reading per-field; handle absent keys (defaults /
      nullable), `DECODE_DONE`, `UNKNOWN_NAME`. Tests for partial / missing fields.

## Phase 5 — Document API

- [ ] **T5.1** `Document<T>`: `get`, `set(REPLACE)`, `delete`, `exists`. Tests.
      Also delivers the `Documents` root factory (`create`, `inMemory`, `document<T>`) that
      api-design §1/§7/§10 requires to obtain a `Document<T>` — see [ADR-0007](adr/0007-documents-root-factory.md).
- [ ] **T5.2** `set(MergeStrategy.UPDATE) { }` builder; UPDATE on missing doc starts from
      defaults. Tests. Builder is `T.() -> T` returning a `copy()`, not a mutated receiver — see
      [ADR-0008](adr/0008-update-builder-returns-copy.md) (api-design §3/§10 corrected to match).
- [ ] **T5.3** `DocumentDecodingException` with key/field/cause. Tests for the failure path.
      Wraps both corrupt-bytes (`SerializationException` cause) and missing-required-field on a
      partially-present doc; `field` is nullable — see [ADR-0009](adr/0009-document-decoding-exception.md).

## Phase 6 — Reactivity

- [ ] **T6.1** `MutableSharedFlow<String>` change bus; emit affected key after commit.
- [ ] **T6.2** `flow()` — initial value + conflated change emissions; null on delete. Tests.
- [ ] **T6.3** `stateFlow(scope)`. Tests.

## Phase 7 — Field delegates

- [ ] **T7.1** `field(prop, default)` `ReadWriteProperty` backed by one key. Tests.
- [ ] **T7.2** `fieldFlow(prop)`. Tests.

## Phase 8 — Polish

- [ ] **T8.1** Dispatcher configuration (default `Dispatchers.IO`).
- [ ] **T8.2** Per-document write mutex for atomic multi-field UPDATE.
- [ ] **T8.3** KDoc on every public entry point.
- [ ] **T8.4** Runnable Android sample (`:sample`) — 10-line getting started.
- [ ] **T8.5** Generate and check API dump (`apiDump`).

## Phase 9 — Benchmarks (v1.x, optional for first tag)

- [x] **T9.1** Microbenchmark write/read vs raw MMKV; record in README. On-device, one shared
      `TimeSource.Monotonic` timing loop on both platforms (`androidDeviceTest` + `iosTest`), not CI
      — Jetpack Microbenchmark was tried and dropped, see [ADR-0014](adr/0014-on-device-benchmarks.md). Cases: `set(REPLACE)`, `get`, `set(UPDATE)`, `delete`,
      field-delegate write, each vs raw MMKV. Results table in `README.md` (fill in after running on
      device). No public ABI change (benchmarks are test-source-only); `checkLegacyAbi` unmoved.
- [x] **T9.2a** iOS: rewrote the raw-MMKV baselines in `DocumentsBenchmark.kt` to do the same
      per-field work as Documents (same 5 field keys, same per-field CBOR encode/decode, same
      prefix-scan-then-remove on clear) instead of one whole-object blob — the old raw baseline was
      not a fair comparison of what the library's abstraction actually costs. Added raw-MMKV
      counterparts for `set{}` update (full get+set round trip, matching `DocumentImpl.set(builder)`
      having no single-field shortcut today), `delete`, and field-delegate write, which previously had
      none. Refreshed README iOS table with the new fair numbers, simulator run
      (`iosSimulatorArm64Test`), median of 20k iterations. Test-source-only; `checkLegacyAbi` unmoved.
- [ ] **T9.2b** *(follow-up)* Android: apply the same fair-comparison rewrite to
      `androidDeviceTest/.../DocumentsBenchmark.kt` (currently still has the old whole-object raw
      baseline) and refresh the README Android table on an otherwise-idle device — that table is
      also still stale from the original JSON-era run.
      the table.

## Phase 10 — iOS storage (v1.0; ships with Android)

Implements the iOS `Storage` so the cross-platform consumer contract is real, not contract-only.
Resolves the [ADR-0012](adr/0012-automatic-mmkv-initialization.md) follow-up. MMKV is bound on Apple via the Kotlin CocoaPods plugin — see
[ADR-0013](adr/0013-ios-mmkv-via-cocoapods.md). Each task below states its dependencies explicitly; tasks without a stated dependency are
independent. The iOS `Storage` impl is `internal`, so the published common ABI must not move —
`checkLegacyAbi` stays green throughout (see [ADR-0012](adr/0012-automatic-mmkv-initialization.md)).

- [x] **T10.1** *(independent)* Add the Kotlin CocoaPods plugin and declare `pod("MMKV")` in
      `documents/build.gradle.kts`; reuse the existing `mmkv` version ref in
      `gradle/libs.versions.toml` for the pod version, and set `ios.deploymentTarget`. The plugin
      generates the MMKV cinterop bindings — see [ADR-0013](adr/0013-ios-mmkv-via-cocoapods.md). Acceptance: the cinterop / `podGen` task
      succeeds and `cocoapods.MMKV.*` symbols resolve from `iosMain`.
- [x] **T10.2** *(depends on T10.1)* Replace the no-op iOS `ensureInitialized()` actual in
      `PlatformStorage.ios.kt` with a once-guarded `MMKV.initializeMMKV(rootDir)` using the
      in-process app sandbox path (no `Context` — preserves the zero-touch contract from [ADR-0012](adr/0012-automatic-mmkv-initialization.md)).
      Resolves the [ADR-0012](adr/0012-automatic-mmkv-initialization.md) follow-up. Acceptance: init runs exactly once and is idempotent across
      repeated `create` calls.
- [x] **T10.3** *(depends on T10.1)* Implement `MmkvStorage` in `iosMain` against the
      `internal interface Storage`, mapping each method to the MMKV Obj-C API
      (`getDataForKey:`/`setData:forKey:`/`removeValueForKey:`/`containsKey:`/`allKeys`) with
      `NSData` ⇄ `ByteArray` conversion and defensive-copy semantics matching `InMemoryStorage`.
      Acceptance: round-trips bytes; `keys(prefix)` filters; `remove` is idempotent.
- [x] **T10.4** *(depends on T10.1, T10.2, T10.3)* Replace the `UnsupportedOperationException` in
      iOS `platformStorage(name, multiProcess)` with `MmkvStorage(MMKV.mmkvWithID(name, mode))`,
      honoring `multiProcess` via the MMKV mode enum — or, if the iOS pod is single-process-only,
      document that here and ignore the flag. Acceptance: `Documents.create("name")` returns a
      working iOS store.
- [x] **T10.5** *(depends on T10.3; mirrors the Android device test)* Add an `iosTest` source set
      and an iOS `MmkvStorageTest` mirroring `behaviorParityWithInMemoryStorage` plus the
      round-trip and persistence-across-recreation cases, run on `iosSimulatorArm64`. Acceptance:
      `./gradlew :documents:iosSimulatorArm64Test` is green.
- [x] **T10.6** *(depends on T10.4)* Confirm the zero-touch contract holds identically on iOS and
      the public ABI is unchanged (iOS impl is `internal`). Acceptance: `checkLegacyAbi` green and
      the published common klib API is unmoved.

## Phase 11 — Binary format & codec cleanup (pre-v0.1.0 tag)

Settle the on-disk format before tagging, while changing it is still a code change and not a data
migration. Decision recorded in [ADR-0015](adr/0015-cbor-internal-format.md) (supersedes [ADR-0006](adr/0006-codec-holds-serializer.md)).

- [x] **T11.1** Switch the on-disk format from JSON to a single internal CBOR instance
      (`Cbor { ignoreUnknownKeys = true }`), encoding field values straight to bytes
      (`encodeToByteArray`/`decodeFromByteArray`, no UTF-8 text hop). Replace the hand-rolled
      `"null"` text sentinel with CBOR's native null via the nullable serializer. Touches
      `DocumentEncoder`, `DocumentDecoder`, `DocumentSerialization`, `Document`, `Documents`, and
      the catalog/build (`kotlinx-serialization-cbor` in, `-json` out). Acceptance: `:documents:check`
      green (existing round-trip/null/decoding tests pass against CBOR).
- [x] **T11.2** Remove the unused format abstraction: delete `Codec<T>`, `KotlinxCodec<T>`, and the
      public `DocumentsConfig.json` property; delete `KotlinxCodecTest`. Acceptance: public ABI is a
      pure shrink; regenerate `documents/api/documents.klib.api` via `updateLegacyAbi` and
      `checkLegacyAbi` is green.
- [x] **T11.3** Make the benchmark raw-MMKV baseline encode with CBOR too (apples-to-apples), so
      both sides of the comparison use the library's format. Acceptance: benchmark sources compile;
      README numbers re-run on device as a follow-up (needs hardware).
