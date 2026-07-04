# Architecture Decision Records

ADRs are numbered sequentially and never renumbered or deleted — a superseded ADR stays in
place with its status updated to point at the ADR that replaced it.

| # | Title | Status | Date |
|---|---|---|---|
| [0001](0001-field-decomposition.md) | Decompose documents into per-field keys | Accepted | 2026-06-15 |
| [0002](0002-sharedflow-reactivity.md) | In-memory SharedFlow change bus for reactivity | Accepted | 2026-06-15 |
| [0003](0003-serialdescriptor-walking.md) | Walk fields via SerialDescriptor, not reflection | Accepted | 2026-06-15 |
| [0004](0004-vocabulary.md) | Keep "document" as the user-facing noun | Accepted | 2026-06-15 |
| [0005](0005-publishing-maven-central.md) | Publish to Maven Central via macOS CI, not JitPack | Accepted | 2026-06-15 |
| [0006](0006-codec-holds-serializer.md) | KotlinxCodec is per-type and holds its KSerializer | Superseded by [0015](0015-cbor-internal-format.md) | 2026-06-16 |
| [0007](0007-documents-root-factory.md) | The `Documents` root factory is built alongside T5.1 | Superseded by [0016](0016-documents-entry-point-and-collections.md) | 2026-06-21 |
| [0008](0008-update-builder-returns-copy.md) | The UPDATE builder returns a new `T` via `copy()`, not a mutated receiver | Accepted (amended by [0017](0017-drop-merge-strategy.md), [0018](0018-update-verb-and-single-field-update.md)) | 2026-06-21 |
| [0009](0009-document-decoding-exception.md) | Decoding failures surface as `DocumentDecodingException`, not a bare `SerializationException` | Accepted | 2026-06-21 |
| [0010](0010-field-delegate-serializer.md) | `field(prop, default)` resolves its serializer via a reified inline extension | Accepted | 2026-06-21 |
| [0011](0011-synchronous-api-nonsuspend-lock.md) | Synchronous document API, non-suspending write lock, dispatcher governs flow collection | Accepted | 2026-06-21 |
| [0012](0012-automatic-mmkv-initialization.md) | Documents owns MMKV initialization (zero-touch auto-init) | Accepted | 2026-06-21 |
| [0013](0013-ios-mmkv-via-cocoapods.md) | Bind MMKV on iOS via the Kotlin CocoaPods plugin | Accepted | 2026-06-21 |
| [0014](0014-on-device-benchmarks.md) | On-device benchmarks, platform-native harnesses | Accepted | 2026-06-21 |
| [0015](0015-cbor-internal-format.md) | Single internal CBOR format; remove the Codec abstraction | Accepted (supersedes [0006](0006-codec-holds-serializer.md)) | 2026-06-21 |
| [0016](0016-documents-entry-point-and-collections.md) | `Documents` is the entry point; `Collection` is the named-file handle | Accepted (supersedes [0007](0007-documents-root-factory.md)) | 2026-06-21 |
| [0017](0017-drop-merge-strategy.md) | Drop `MergeStrategy`; the overloads carry the intent | Accepted (amends [0008](0008-update-builder-returns-copy.md)) | 2026-06-21 |
| [0018](0018-update-verb-and-single-field-update.md) | `set(builder)` becomes `update(builder)`; add `update(prop, value)` for single-field writes | Accepted (amends [0008](0008-update-builder-returns-copy.md)) | 2026-07-02 |
| [0019](0019-drop-multiprocess-mode.md) | Drop `multiProcess`; storage is always single-process | Accepted | 2026-07-03 |
| [0020](0020-align-package-with-groupid.md) | Align Kotlin package with Maven groupId (`com.nomemmurrakh`) | Accepted | 2026-07-03 |
| [0021](0021-field-decorator-extension-point.md) | `FieldDecorator` — a bytes-in/bytes-out extension point for field values | Accepted | 2026-07-04 |
