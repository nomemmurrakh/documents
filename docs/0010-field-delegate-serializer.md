# ADR-0010: `field(prop, default)` resolves its serializer via a reified inline extension

**Status:** Accepted
**Date:** 2026-06-21

## Context

T7.1 implements the property delegate from api-design §5:

```kotlin
var theme: Theme by doc.field(SettingsData::theme, default = Theme.SYSTEM)
```

A `field(prop, default)` delegate reads and writes exactly one decomposed key,
`{doc}::{prop.name}`, holding the JSON-encoded bytes of that single field's value (the same
on-disk scheme the decomposition encoder/decoder use, ADR-0001/0003). To read or write that
key the delegate needs a `KSerializer<V>` for the field's value type `V`.

The decomposition encoder/decoder never face this problem: they walk the **parent** `T`'s
`SerialDescriptor` and obtain each field's serializer from the descriptor element during the
walk. `field()` operates on a single field in isolation, with only a `KProperty1<T, V>` in
hand. kotlinx.serialization offers no way to derive a `KSerializer` from a `KProperty` value
at runtime — there is no reflection bridge on KMP/native. So the bare `field(prop, default)`
signature in §5 cannot, on its own, obtain the serializer; the implementation must supply it.

`prop.name` is still used — it is the field's key suffix.

## Decision

Make `field` a **reified inline extension** on `Document<T>` that resolves the serializer from
the type parameter via `serializer<V>()`:

```kotlin
public inline fun <reified V> Document<*>.field(
    prop: KProperty1<*, V>,
    default: V,
): ReadWriteProperty<Any?, V> =
    field(prop.name, default, serializer())
```

The reified `V` is inferred from the property reference, so the call site matches api-design §5
**exactly** — the user passes nothing extra:

```kotlin
var theme by doc.field(SettingsData::theme, default = Theme.SYSTEM)
```

The inline extension forwards to a `@PublishedApi internal` member that does the real work
(read single key → `default` if absent; write single key → emit on the change bus). It is
`@PublishedApi internal` rather than `public` because a public inline body may only reference
public or `@PublishedApi internal` symbols, and we do not want the field-name/serializer
overload in the public surface.

This mirrors the existing `document<T>(key)` reified extension (`Documents.document`), which
already resolves `serializer<T>()` the same way — one mental model for serializer resolution
across the library, not two.

## Consequences

**Positive**
- Call site is exactly the documented §5 form; zero user-facing ceremony, reads as an
  idiomatic Kotlin delegate.
- Consistent with `document<T>(key)`'s reified-extension pattern.
- No new runtime failure mode tied to `@SerialName` mismatches (we never look the field up by
  name inside a descriptor).

**Negative / cost**
- The public inline body ships in the ABI, and requires a `@PublishedApi internal` bridge
  member on `Document`/`DocumentImpl`.
- `serializer<V>()` resolves the standard serializer for `V`; a field relying on a custom or
  contextual serializer declared on the parent `T` is **not** honored here. v1 does not support
  custom per-field serializers, so this introduces no new limitation. If that changes,
  migrating to "walk the parent descriptor" is source-compatible — the §5 call site does not
  change — so this choice is not a dead end.

## Alternatives considered

- **Explicit `serializer: KSerializer<V>` parameter** — `field(prop, default, serializer)`.
  Simplest ABI (non-inline) but forces every delegate to hand-write `.serializer()`, a
  per-field paste-error risk, and it is the only option that breaks the §5 call site. Rejected.
- **Walk the parent `T` descriptor by `prop.name`** — keeps `field(prop, default)` non-reified
  and reuses the exact element serializer the decomposition path uses, guaranteeing `field()`
  and `get()`/`set()` can never disagree on encoding (and honoring custom per-field
  serializers). But it needs `DocumentImpl` to expose its `KSerializer<T>`, a by-name →
  element-index → element-serializer lookup, and handling for renamed (`@SerialName`), nested,
  nullable, and enum element serializers — more machinery and a new runtime failure mode, for a
  consistency guarantee that only matters once custom per-field serializers exist (a non-goal
  in v1). Rejected for v1; reachable later without a call-site change.

## Addendum (T7.2): `fieldFlow` takes the same `default`

`fieldFlow(prop): Flow<V>` (api-design §5/§10, original form) has the same root limitation as
`field`: a cold `Flow` must emit a value on collection even when the field was never written,
but a field's **declared default** is no more recoverable from a `KProperty` at runtime than
its serializer is. The constructor default lives in the generated `@Serializable` machinery of
`T`; a `SerialDescriptor` exposes only *whether* an element is optional (`isElementOptional`),
never the default value itself, and `KProperty1.get(instance)` is reflective member access
unavailable on KMP/native (and there is no instance to read from anyway).

Decision: `fieldFlow` takes the same caller-supplied `default` as `field`, returning a non-null
`Flow<V>`:

```kotlin
public inline fun <reified V> Document<*>.fieldFlow(prop: KProperty1<*, V>, default: V): Flow<V>

val themeFlow: Flow<Theme> = doc.fieldFlow(SettingsData::theme, default = Theme.SYSTEM)
```

Semantics: emits the current field value (or `default` when the key is absent) on collection,
re-reads and emits on each committed write to the **document**, drops a re-read that did not
change the field value (`distinctUntilChanged`) so a sibling field's write does not double-emit
this field, and conflates. This keeps `fieldFlow` symmetric with `field(prop, default)`, reuses
the existing `readField` bridge unchanged, and never leaks a `null` for a field that is non-null
once set. api-design §5/§10 are corrected to `fieldFlow(prop, default)`.

Rejected: **`Flow<V?>` (no default, null for unset)** — keeps the original §5 call site but is
asymmetric with `field`'s non-null `V` and forces every collector to handle `null` forever;
**derive from whole-doc `flow()`** — matches §5/§10 verbatim but needs `prop.get(instance)`
reflection (unavailable on KMP/native) and throws on an absent required-field document.
