# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-10-21

The public API is unchanged. Existing code compiles and behaves as before.

### Added
- **Instances of the same document now share one change bus and one set of write locks**
  ([ADR-0022](adr/0022-process-wide-store-runtime.md)). A write through one instance reaches
  `flow()`, `stateFlow()`, and `fieldFlow()` on every other instance of the same document, whether
  the instances come from dependency injection, repeated `Documents.document(...)` calls, or
  separate `Documents.collection(name)` calls. Sharing a single instance is no longer required,
  though it is still the recommended approach when it is practical.
- **Concurrent updates through different instances are no longer lost.** Each document has its own
  lock, striped over 64 locks that are created lazily, so unrelated documents do not wait for each
  other.
- **Distinct `DocumentDecodingException` messages for each cause**
  ([ADR-0023](adr/0023-decoding-failure-messages.md)): updating a document that was never written
  when a field has no default, a stored document missing a required field, a stored field that
  cannot be read as its type, and a field decorator that failed to unwrap. Each message says what
  went wrong and how to fix it. Messages name the cause's exception class and never its text, so a
  stored value cannot reach a log.
- A **Shared Instances** screen in the sample app that shows two instances of one document staying
  in sync.

### Changed
- Clarified the single-process limit in the documentation. An ordinary Android `Service` runs in the
  app's own process and is safe. A component with `android:process`, or an iOS app extension, runs
  in a separate process and is not supported. Tested on an Android emulator: a service in a separate
  process lost about 35 to 45 percent of updates to the same store.
- Updated dependencies: Kotlin 2.4.21, Android Gradle Plugin 9.3.3, Gradle 9.5.0, MMKV 2.4.3,
  kotlinx.serialization 1.12.0, Compose Multiplatform 1.12.2, and compile and target SDK 37.
- Added the Android ABI reference dump (`documents/api/android/documents.api`) to API validation.
- Rewrote the documentation site and README for v0.2.0.

## [0.1.0] - 2026-07-05

### Added
- Initial project scaffolding and design documentation (PRD, API design, architecture, ADRs,
  roadmap, task breakdown, test plan).

### Removed
- **`CollectionConfig.multiProcess`** ([ADR-0019](adr/0019-drop-multiprocess-mode.md)). Storage is now always opened single-process;
  concurrent access to the same MMKV store from more than one OS process is unsupported. The flag
  made storage safe to share across processes but `flow()`/`stateFlow()` never observed writes
  from other processes reactively — a correctness trap removed rather than documented around.
  **Breaking API change** (pre-1.0): any `collection(name) { multiProcess = ... }` call site fails
  to compile; drop the assignment.

### Changed
- **Renamed the Kotlin package from `io.github.nomemmurrakh.documents` to
  `com.nomemmurrakh.documents`** ([ADR-0020](adr/0020-align-package-with-groupid.md)), matching the Maven groupId the library already
  publishes under. Android `namespace`/`applicationId` (library and sample) were renamed to match.
  **Breaking API change** (pre-1.0): every `import io.github.nomemmurrakh.documents.*` call site
  must become `import com.nomemmurrakh.documents.*`.
- **Renamed the update path to `update`, and its builder is now explicit-parameter, not
  receiver-style** ([ADR-0018](adr/0018-update-verb-and-single-field-update.md)). `set(builder: T.() -> T)` is now `update(builder: (T) -> T)`,
  modeled on `kotlinx.coroutines.flow.MutableStateFlow.update { current -> ... }`. **Breaking API
  change** (pre-1.0): every `doc.set { copy(...) }` call site becomes
  `doc.update { current -> current.copy(...) }`, with bare property reads inside the builder now
  requiring an explicit `current.` prefix.
- **Added `Document<T>.update(prop, value)`** for direct single-field writes ([ADR-0018](adr/0018-update-verb-and-single-field-update.md)). Reuses
  the same single-key write path `field()`'s delegate already uses (`DocumentImpl.writeField`) —
  no read of the rest of the document. `field()`/`fieldFlow()` are unchanged.
- **Dropped `MergeStrategy`** ([ADR-0017](adr/0017-drop-merge-strategy.md)). The overloads now carry the intent: `set(value)`
  replaces the whole document, `set { }` updates it (builder over the current value, or defaults
  when absent). **Breaking API change** (pre-1.0): `set(MergeStrategy.UPDATE) { }` becomes
  `set { }`; `set(MergeStrategy.REPLACE) { }` must be rewritten as a whole-object `set(value)`.
  (Superseded by the `update` rename above.)
- **Reworked the entry point** ([ADR-0016](adr/0016-documents-entry-point-and-collections.md)). `Documents` is now an entry-point object, not the
  per-file handle. Open a document on the default store with `Documents.document<T>(key)`, or open
  a named `Collection` (its own MMKV file) with `Documents.collection(name)` then
  `collection.document<T>(key)`. `Documents.inMemory()` now returns a `Collection`. **Breaking
  API change** (pre-1.0): `Documents.create(name).document(key)` is replaced by the above.
- `DocumentsConfig` is split into `DocumentConfig` (`dispatcher`) for the default store and
  `CollectionConfig` (`multiProcess`, `dispatcher`) for collections ([ADR-0016](adr/0016-documents-entry-point-and-collections.md)).
- **On-disk format is now CBOR (binary) instead of JSON** ([ADR-0015](adr/0015-cbor-internal-format.md)). Field values are encoded
  straight to bytes with no UTF-8 text intermediate. **Breaking storage-format change:** data
  written by an earlier JSON build will not decode; no migration is provided (pre-1.0).

### Removed
- Public `Codec<T>` interface and `KotlinxCodec<T>` class, and the `DocumentsConfig.json`
  configuration property. The serialization format is now a single internal CBOR instance with
  no public extension point ([ADR-0015](adr/0015-cbor-internal-format.md)).

[Unreleased]: https://github.com/nomemmurrakh/documents/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/nomemmurrakh/documents/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/nomemmurrakh/documents/releases/tag/v0.1.0
