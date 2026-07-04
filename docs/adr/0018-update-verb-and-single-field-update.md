# ADR-0018: `set(builder)` becomes `update(builder)`; add `update(prop, value)` for single-field writes

**Status:** Accepted
**Date:** 2026-07-02
**Amends:** ADR-0008 (receiver style and verb)

## Context

`set` currently has two overloads with very different intents:

```kotlin
user.set(User(...))          // replace: an entire object is supplied
user.set { copy(name = "…") } // update: a builder runs over the current value
```

Both are named `set`. The overload shape alone (ADR-0017) already disambiguates them at the call
site — a one-arg value call reads as "replace," a trailing-lambda call reads as "update" — but the
shared verb still requires a reader to look at the argument shape to know which one they're
looking at, and "set" carries no hint that the builder path does a read first. Naming the two
paths differently, not just differently-shaped, removes that ambiguity outright.

Separately, the update builder today is `T.() -> T`: the current value is an implicit receiver,
and the builder body calls a bare `copy(...)`. This reads compactly but has two costs: (1) it is
unfamiliar next to the shape most Kotlin developers already know from
`kotlinx.coroutines.flow.MutableStateFlow.update { current -> current.copy(...) }`, which uses an
explicit parameter, and (2) inside a builder with more than a trivial expression, an implicit
receiver makes it easy to lose track of what "bare" identifiers resolve to, especially once a
nested `@Serializable` field's own `copy()` is involved (`player.copy(hp = player.hp - 10)` reads
ambiguously as to which `copy` receiver `player` belongs to without a `current.` prefix that an
explicit parameter forces).

Finally, every update today is a read-modify-write: `update { }` (formerly `set { }`) always calls
`get()` (or falls back to defaults), applies the builder, and writes the whole result back, even
when only one field actually changed. For a document with many fields, changing one field costs a
full decode of every field. `field()`'s `ReadWriteProperty` delegate already proves the single-key
write path is cheap and correct (`DocumentImpl.writeField` — one `storage.putBytes` call, no read)
but it requires declaring a delegated property ahead of time; there was no direct, one-shot way to
do a single-field write without either standing up a delegate or paying for a full builder
round trip.

## Decision

Rename `set(builder: T.() -> T)` to `update(builder: (T) -> T)`, and change the builder from
receiver-style to explicit-parameter style:

```kotlin
public fun update(builder: (T) -> T)

user.update { current ->
    current.copy(name = "Khuram M.")
}
```

This is a naming and ergonomics change only. The builder still must return a new `T` via `copy()`
— ADR-0008's core reasoning (the domain type stays an immutable `@Serializable` data class; no
`var`-mutation surface is introduced) is unchanged and is not being re-litigated here. `(T) -> T`
still returns a new immutable value; it simply takes `current` as an explicit parameter instead of
an implicit receiver, matching `MutableStateFlow.update { current -> ... }`. `update`'s baseline,
locking, and defaults-on-absent semantics are unchanged from the old `set(builder)`
(ADR-0008/0017 stand).

Add a second `update` overload for a genuinely different intent — a single-field write with no
read of the rest of the document:

```kotlin
public inline fun <T, reified V> Document<T>.update(prop: KProperty1<T, V>, value: V)
```

This is a top-level reified inline extension function, not an interface member — interfaces
cannot declare inline abstract members, and `reified V` is required to resolve `KSerializer<V>` at
compile time (the same constraint `field`/`fieldFlow` already satisfy this way; there is no
runtime path from a `KProperty` to its `KSerializer`, and Kotlin/Native has no general reflection).
It is typed `KProperty1<T, V>`, not `KProperty1<*, V>` like `field()`/`fieldFlow()` — because it is
declared as an extension on the concrete `Document<T>` (not `Document<*>`), the compiler already
knows `T` at the declaration site, so the tighter bound is available for free and catches a
property from the wrong document type at compile time. `field()`/`fieldFlow()` keep their existing
`KProperty1<*, V>` signature; retroactively tightening those is a separate, unrequested change and
is out of scope here.

`update(prop, value)` reuses `DocumentImpl.writeField` — the exact mechanism already backing
`field()`'s delegate `setValue` — with no changes to `writeField` itself. It performs one
`storage.putBytes` and one `changes.emit(key)`; it never calls `get()`/`decodeDocument`.

Two overloads now share the name `update`:

```kotlin
public fun update(builder: (T) -> T)                                    // member; whole-object, read-modify-write
public fun <T, V> Document<T>.update(prop: KProperty1<T, V>, value: V)  // extension; single-field, no read
```

They do not collide: one takes a single `Function1` argument and is called with trailing-lambda
syntax (`doc.update { current -> ... }`), the other takes two positional arguments of unrelated
types (`KProperty1<T, V>`, `V`) and is called `doc.update(Prop, value)`. Different arity, and
Kotlin does not attempt a SAM/lambda conversion of a two-argument call against a one-argument
`Function1` parameter, so there is no overload-resolution ambiguity at any real call site.

## Consequences

**Positive**
- `update` unambiguously names the read-modify-write path; `set` is now exclusively "replace,"
  matching how `set`/`update` read in `MutableStateFlow` and reducing the learning cost for anyone
  who already knows that API.
- Explicit-parameter builders remove the implicit-receiver ambiguity that showed up around nested
  `copy()` calls (e.g. `player.copy(...)` inside an outer builder).
- `update(prop, value)` gives a true O(1) single-field write with no delegate boilerplate for
  one-off writes, completing the cost story `field()` started: users now have a direct call for
  the same cheap path `field()`'s `setValue` already uses internally.
- No new public type, no enum — continues the ADR-0017 precedent of letting overload shape (now
  also verb + shape together) carry intent instead of a strategy parameter.

**Negative / cost**
- **Breaking API change** (pre-1.0): every `set { }` call site becomes `update { current -> ... }`,
  with every bare property/`copy()` reference inside the lambda body needing an explicit `current.`
  prefix. All in-repo call sites, README, and api-design.md are updated in this same change; there
  is no deprecation cycle (consistent with how ADR-0016/0017 were handled).
- Two functions now share the name `update` with different call shapes (member function vs.
  extension function); a reader unfamiliar with the library must learn both shapes exist. Judged
  acceptable because the shapes are maximally distinct (trailing lambda vs. two positional args)
  and the naming precedent (`MutableStateFlow.update`) makes the whole-object one immediately
  recognizable; the second overload is additive, not a compromise on the first.
- `update(prop, value)` is unable to delete a field / reset it to "unset" — like `field()`'s
  `setValue`, it always writes a value, never removes a key. This mirrors `field()`'s existing
  contract and is not a new limitation introduced here.
- Because `update(prop, value)` is an extension function, it must be imported at call sites
  outside the library's package — exactly like `field`/`fieldFlow` already require today. Not a
  new cost class, just worth noting alongside the member overload's lack of an import requirement.

## Alternatives considered

- **Keep `T.() -> T` receiver style, only rename `set` to `update`.** Rejected: the goal is
  explicit-parameter style specifically for `MutableStateFlow`-style familiarity; a rename alone
  doesn't address the receiver-vs-parameter ergonomics gap that motivated this ADR.
- **Name the single-field overload something other than `update`, e.g. `patch`/`writeField`.**
  Considered, but `update` for both keeps a single verb for "change this document," with the
  overload's parameter shape doing the disambiguation — consistent with ADR-0017's precedent that
  overload shape, not a new name or enum, carries intent. Two truly different verbs would also
  fragment the mental model of "how do I change part of a document" into two lookups instead of
  one. Rejected in favor of the shared-verb, distinct-shape approach.
- **Make `update(prop, value)` an interface member instead of an extension.** Not possible without
  losing `reified`, since interfaces cannot declare inline abstract members and `reified` requires
  `inline`; a non-reified member would need the caller to pass a `KSerializer<V>` explicitly,
  which is exactly the ergonomic cost `field`/`fieldFlow`'s extension-function pattern already
  avoids. Rejected — extension-function-with-reified-bridge is the established, working pattern.
- **Type `update(prop, value)`'s `KProperty1` as `KProperty1<*, V>`, matching `field()`.**
  Considered for consistency with `field()`'s signature, but rejected: unlike `field()`, this
  function is declared on a concrete `Document<T>` receiver, so `T` is known and the tighter,
  safer `KProperty1<T, V>` bound is available at zero cost. Matching `field()`'s looser bound here
  would give up a real compile-time safety win for no benefit.
