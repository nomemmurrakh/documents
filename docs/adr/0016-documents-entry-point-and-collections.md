# ADR-0016: `Documents` is the entry point; `Collection` is the named-file handle

**Status:** Accepted
**Date:** 2026-06-21
**Supersedes:** ADR-0007 (the `Documents` root factory shape)

## Context

The original surface (ADR-0007, api-design §1/§7/§10) made `Documents` itself the per-file
handle: you called `Documents.create(name)` to bind an MMKV instance, then `.document<T>(key)`
on the returned `Documents`. In practice this reads awkwardly:

```kotlin
Documents.create("game").document<GameSave>("slot-1")
```

Two problems surfaced in use:

1. **"create" oversells.** `create(name)` then `document(key)` *opens* a document — calling it
   again returns the same persisted document, it does not create a second one. The verb implies
   construction; the behavior is get-or-open.
2. **A file is forced on every call.** `name` maps to `MMKV.mmkvWithID(name)`, i.e. a separate
   `.mmkv` file (own mmap, cache, lock, CRC). But MMKV is designed to hold *many keys in one
   file* — a separate file is warranted only for a distinct **lifecycle or access pattern**:
   wipe-on-logout caches, per-user scoping, multi-process sharing, an encryption boundary, or
   hot/cold mmap separation. Most apps want one file and many documents (mirroring MMKV's own
   `defaultMMKV()`), yet the old API made the file concept mandatory and primary.

## Decision

Split the surface into a **default-file fast path** and an explicit **named-file handle**.

```kotlin
object Documents {
    // Default MMKV file. The common case: "Documents creates documents."
    fun <T> document(key: String, serializer: KSerializer<T>, config: DocumentConfig.() -> Unit = {}): Document<T>

    // A separate MMKV file, for a distinct lifecycle/access pattern.
    fun collection(name: String, config: CollectionConfig.() -> Unit = {}): Collection

    // In-memory collection for tests.
    fun inMemory(): Collection
}

interface Collection {
    fun <T> document(key: String, serializer: KSerializer<T>): Document<T>
}

// reified extensions
inline fun <reified T> Documents.document(key: String, noinline config: DocumentConfig.() -> Unit = {}): Document<T>
inline fun <reified T> Collection.document(key: String): Document<T>
```

- `Documents.document<T>(key)` opens a document in the **default store**, a fixed reserved MMKV
  ID (`"documents.default"`) routed through the existing `platformStorage` path — no new
  `expect/actual`, identical on Android and iOS. Its config exposes **`dispatcher` only**
  (`multiProcess` is meaningless for the app's default file).
- `Documents.collection(name)` opens a **named MMKV file** and returns a `Collection`; you then
  call `.document<T>(key)` on it. Its config exposes **`multiProcess` + `dispatcher`**.
- `Documents.inMemory()` returns an in-memory `Collection` (was: an in-memory `Documents`).
- The old `Documents` *interface* (the thing carrying `document()`) becomes the **`Collection`**
  interface. `Documents` becomes a non-instantiable entry-point object holding the factories.
- `DocumentsConfig` splits into `DocumentConfig` (`dispatcher`) and `CollectionConfig`
  (`multiProcess`, `dispatcher`).

The name `Collection` is chosen to match the project's vocabulary ("a collection of documents").
It is *not* a database collection — NG1 (no querying/indexing/collections-as-a-table) is
unchanged; this is purely a named group of documents sharing one file.

`document()` keeps eager key validation via `Keys.prefix(key)` (the `::` rejection), unchanged.

## Consequences

**Positive**
- The common case is one call: `Documents.document<Profile>("me")`, no file concept to learn.
- The file concept survives, named honestly, only where it earns its place (`collection`).
- Return type stays `Document<T>` (bound with `=`, full `get`/`set`/`flow`/`delete` API); `by`
  was considered and rejected — it would collapse the handle to a bare `T?` value (see README
  discussion).

**Negative / cost**
- Breaking public-API + ABI change. Regenerate `documents/api/documents.klib.api`; bump the
  unreleased CHANGELOG. Pre-1.0, no deprecation cycle.
- Supersedes ADR-0007 and amends api-design §1/§2/§7/§10.

## Alternatives considered

- **Keep `create(name).document(key)`** — rejected: forces the file concept on every caller and
  the verb misleads.
- **One-shot `Documents.create<T>()` with no key/name** — rejected: assumes one type = one
  document = one file, which kills document multiplicity (`"me"` vs `"partner"`) and the
  many-documents-per-file model MMKV is built for.
- **`val x by Documents…`** — rejected: a delegate would make `x` the value `T?`, discarding the
  document's reactive/lifecycle API.
- **Name the handle `DocumentCollection`** — rejected in favor of the shorter `Collection`; the
  `kotlin.collections.Collection` clash only bites on a star-import and is in-package here.
