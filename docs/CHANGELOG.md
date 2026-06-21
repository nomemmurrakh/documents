# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project scaffolding and design documentation (PRD, API design, architecture, ADRs,
  roadmap, task breakdown, test plan).

### Changed
- **On-disk format is now CBOR (binary) instead of JSON** (ADR-0015). Field values are encoded
  straight to bytes with no UTF-8 text intermediate. **Breaking storage-format change:** data
  written by an earlier JSON build will not decode; no migration is provided (pre-1.0).

### Removed
- Public `Codec<T>` interface and `KotlinxCodec<T>` class, and the `DocumentsConfig.json`
  configuration property. The serialization format is now a single internal CBOR instance with
  no public extension point (ADR-0015).

[Unreleased]: https://github.com/nomemlabs/documents/commits/main
