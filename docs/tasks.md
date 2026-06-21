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
      only and cannot compile Apple klibs; see ADR-0005). Sub-tasks:
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
      api-design §1/§7/§10 requires to obtain a `Document<T>` — see ADR-0007.
- [ ] **T5.2** `set(MergeStrategy.UPDATE) { }` builder; UPDATE on missing doc starts from
      defaults. Tests. Builder is `T.() -> T` returning a `copy()`, not a mutated receiver — see
      ADR-0008 (api-design §3/§10 corrected to match).
- [ ] **T5.3** `DocumentDecodingException` with key/field/cause. Tests for the failure path.
      Wraps both corrupt-bytes (`SerializationException` cause) and missing-required-field on a
      partially-present doc; `field` is nullable — see ADR-0009.

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

- [ ] **T9.1** Microbenchmark write/read vs raw MMKV; record in README.

## Phase 10 — iOS storage (v1.0; ships with Android)

Implements the iOS `Storage` so the cross-platform consumer contract is real, not contract-only.
Resolves the ADR-0012 follow-up. MMKV is bound on Apple via the Kotlin CocoaPods plugin — see
ADR-0013. Each task below states its dependencies explicitly; tasks without a stated dependency are
independent. The iOS `Storage` impl is `internal`, so the published common ABI must not move —
`checkLegacyAbi` stays green throughout (see ADR-0012).

- [x] **T10.1** *(independent)* Add the Kotlin CocoaPods plugin and declare `pod("MMKV")` in
      `documents/build.gradle.kts`; reuse the existing `mmkv` version ref in
      `gradle/libs.versions.toml` for the pod version, and set `ios.deploymentTarget`. The plugin
      generates the MMKV cinterop bindings — see ADR-0013. Acceptance: the cinterop / `podGen` task
      succeeds and `cocoapods.MMKV.*` symbols resolve from `iosMain`.
- [x] **T10.2** *(depends on T10.1)* Replace the no-op iOS `ensureInitialized()` actual in
      `PlatformStorage.ios.kt` with a once-guarded `MMKV.initializeMMKV(rootDir)` using the
      in-process app sandbox path (no `Context` — preserves the zero-touch contract from ADR-0012).
      Resolves the ADR-0012 follow-up. Acceptance: init runs exactly once and is idempotent across
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
