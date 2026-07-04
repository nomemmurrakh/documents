# ADR-0003: Walk fields via SerialDescriptor, not reflection

**Status:** Accepted
**Date:** 2026-06-15

## Context

Field decomposition (ADR-0001) requires enumerating an object's fields at runtime to map
each to a key. The two options are JVM reflection or kotlinx.serialization's
`SerialDescriptor`.

## Decision

Use `SerialDescriptor` via a custom `CompositeEncoder`/`CompositeDecoder`. No reflection.

## Consequences

**Positive**
- KMP/native compatible — reflection is unavailable or limited off the JVM; `SerialDescriptor`
  is not.
- Reuses the `@Serializable` contract users already provide; no extra annotations.
- Field names, indices, and element descriptors come directly from the descriptor.

**Negative / cost**
- Implementing custom encoder/decoder against the kotlinx.serialization API is the steepest
  part of the build (handling `DECODE_DONE`, `UNKNOWN_NAME`, optionals, nullables).
- Tightly couples the library to `T` being `@Serializable`.

## Alternatives considered

- **Kotlin/Java reflection** — simplest on JVM but breaks KMP and is slower. Rejected.
- **KSP code generation** — could generate per-type accessors, but adds a processor
  dependency and build complexity not justified for v1. Revisit if descriptor walking proves
  a bottleneck.
