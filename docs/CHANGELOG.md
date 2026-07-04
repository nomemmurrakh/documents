# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/nomemmurrakh/documents/commits/master
