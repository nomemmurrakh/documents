# ADR-0006: KotlinxCodec is per-type and holds its KSerializer

**Status:** Accepted
**Date:** 2026-06-16

## Context

The public `Codec<T>` interface (api-design §6) is:

```kotlin
interface Codec<T> {
    fun encode(value: T): ByteArray
    fun decode(bytes: ByteArray, deserializer: KSerializer<T>): T
}
```

`encode` takes no serializer, but kotlinx.serialization cannot serialize a value of an erased
type parameter `T` without a `KSerializer<T>` (there is no reliable reified/reflective path in
`commonMain`/native). `decode`, by contrast, is handed its `deserializer`.

api-design §1 illustrates construction as `KotlinxCodec(Json { ignoreUnknownKeys = true })` — a
single, type-erased, store-wide codec with no serializer. That illustration cannot satisfy
`encode` for an arbitrary `T`.

In the actual call path this asymmetry is harmless: the decomposition layer (ADR-0003, tasks
T4.x) walks the parent object's `SerialDescriptor` and therefore already holds each field's
`KSerializer`. The codec is always used in a context where the serializer is known.

## Decision

`KotlinxCodec<T>` is **per-type** and holds both the `Json` instance and the field's
`KSerializer<T>`:

```kotlin
public class KotlinxCodec<T>(
    private val serializer: KSerializer<T>,
    private val json: Json = Json,
) : Codec<T>
```

- `encode(value)` uses the held `serializer`.
- `decode(bytes, deserializer)` uses the passed-in `deserializer` (honoring the interface), which
  in practice is the same serializer.

The decomposition layer constructs a `KotlinxCodec` per field from the serializer it already has.
The `KotlinxCodec(Json { ... })` form in api-design §1 is therefore a *configuration* of the JSON
format that is applied per field internally, not a single shared instance — api-design will be
updated to reflect this.

## Consequences

**Positive**
- Honors the locked `Codec<T>` interface without reflection.
- Type-safe; no runtime serializer lookup, no native/KMP reflection limitations.
- Custom-codec extension point (api-design §6) is preserved: users still implement `Codec<T>`.

**Negative / cost**
- One `KotlinxCodec` instance per field type rather than one per store. Cheap (holds two
  references) but more allocations than a single shared codec.
- api-design §1's constructor illustration is now inaccurate and must be corrected.

## Alternatives considered

- **Type-erased `KotlinxCodec(json)` + reflective `serializer(value::class)` in `encode`** —
  matches §1 literally but is fragile for generics and nullable types and pulls in reflection
  that is unreliable on native. Rejected.
- **Add a serializer parameter to `Codec.encode`** — would make the interface symmetric, but
  changes the locked public surface in api-design §6 for no consumer benefit. Rejected.
