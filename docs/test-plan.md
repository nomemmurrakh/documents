# Documents — Test Plan

**Last updated:** 2026-06-15

> Tests are written against *intent*, from this document — not reverse-engineered from the
> implementation (which would only confirm its own bugs). Most run in `commonTest` against
> `InMemoryStorage`; MMKV-specific behavior runs in `androidTest`.

---

## 1. Round-trip correctness

- A multi-field `@Serializable` object survives `set` → `get` unchanged.
- Each field is stored under its own `{doc}::{field}` key.
- Enums, nullable fields, default values, and a nested `@Serializable` field all round-trip.

## 2. Partial / missing data

- `get()` on an absent document returns `null` (no throw).
- A document with some keys missing decodes using field defaults / null.
- `update { current -> ... }` on a missing document starts from defaults and persists only set
  fields.

## 3. Merge semantics

- `set(value)` overwrites all fields, clearing any field not present in the new value.
- `update { current -> ... }` changes only touched fields; untouched fields keep persisted values.
- Multi-field `update { }` is atomic from an observer's perspective (one emission, all-or-nothing).

## 4. Existence & deletion

- `exists()` true after `set`, false after `delete`.
- `delete()` removes all `{doc}::*` keys for that document.
- `get()` returns `null` and `flow()` emits `null` after delete.

## 5. Reactivity

- `flow()` emits the current value immediately on collection.
- `flow()` emits the new value after each committed write; emissions are conflated.
- `flow()` emits `null` on delete.
- No emission occurs for a write to a *different* document key.
- `fieldFlow(prop)` emits only when that specific field changes.
- Emission happens only after the write is committed (never uncommitted state).

## 6. Field delegates

- Reading a never-set delegated field returns its `default`.
- Writing a delegated field updates only that field's key.
- A delegated write triggers the document's `flow()` and the field's `fieldFlow()`.

## 6a. Single-field update (`update(prop, value)`)

- `update(prop, value)` writes only that field's decomposed key; it does not touch other field
  keys of the same document.
- `update(prop, value)` triggers the document's `flow()`.
- `update(prop, value)` does not perform a read of the document — a corrupt *other* field's bytes
  must not prevent a successful `update(prop, value)` write, unlike `update { }` which would fail
  decoding first.

## 7. Error handling

- Corrupt / undecodable bytes throw `DocumentDecodingException` naming the document key and
  field, with the underlying cause attached.
- Using `::` in a document key is rejected with a clear message.

## 8. Concurrency

- Concurrent writes to the same document do not interleave field writes (mutex holds).
- Concurrent reads during a write never observe a half-applied UPDATE.

## 9. Codec

- A custom `Codec<T>` is honored over the default.
- `KotlinxCodec` configuration (e.g. `ignoreUnknownKeys`) is respected.

## 10. Platform (androidTest)

- `MmkvStorage` persists across instance recreation (durability).
- Behavior parity: the same semantics suite passes against `MmkvStorage` and
  `InMemoryStorage`.

## 11. API governance (CI)

- `checkKotlinAbi` (Kotlin built-in ABI validation) passes — no unintended public API changes.
- `explicitApi()` strict produces no violations.
