# ADR-0022: A process-wide store runtime shares the change bus and write locks

**Status:** Accepted (amends [0002](0002-sharedflow-reactivity.md), [0011](0011-synchronous-api-nonsuspend-lock.md))
**Date:** 2026-10-20

## Context

Today the change bus and the write lock are owned by object instances, not by the store they
operate on:

- `ChangeBus` is created inside `CollectionImpl`, and `Documents.document(...)` and
  `Documents.collection(...)` build a new `CollectionImpl` on every call.
- `DocumentImpl` creates its own `reentrantLock()` per instance.

So two `Documents.document("settings")` calls produce two documents with separate buses and
separate locks. Storage itself is unaffected, because MMKV returns the same underlying store
for the same ID and data is always consistent. Only in-process coordination is split, which
causes two failures:

1. **Missed notifications.** A write through one instance never reaches `flow()`,
   `stateFlow()`, or `fieldFlow()` collectors of another instance of the same document.
2. **Lost updates.** `update { }` is a read-modify-write under the per-instance lock. Two
   instances of one document do not exclude each other, so concurrent updates can both read
   the same base value and one overwrites the other.

With a DI framework the developer can inject a single instance everywhere and avoid both. That
makes correctness depend on the consumer never opening a second instance, which is friction
the library should absorb.

## Decision

Introduce an internal, process-wide `Runtime` object that owns one `StoreRuntime` per store ID
for the lifetime of the process. `StoreRuntime` holds the storage handle, the change bus, and
the write locks for that store.

```kotlin
internal class StoreRuntime(val storage: Storage) {

    val changes: ChangeBus = ChangeBus()

    private val guard = reentrantLock()
    private val stripes = arrayOfNulls<ReentrantLock>(STRIPE_COUNT)

    fun lockFor(key: String): ReentrantLock {
        val index = (key.hashCode() and Int.MAX_VALUE) % STRIPE_COUNT
        return guard.withLock {
            stripes[index] ?: reentrantLock().also { stripes[index] = it }
        }
    }

    private companion object {
        const val STRIPE_COUNT = 64
    }
}

internal object Runtime {

    private val guard = reentrantLock()
    private val stores = mutableMapOf<String, StoreRuntime>()

    fun store(id: String, openStorage: () -> Storage): StoreRuntime = guard.withLock {
        stores.getOrPut(id) { StoreRuntime(openStorage()) }
    }
}
```

`Documents.document(...)` resolves its store through `Runtime.store(DEFAULT_STORE_ID)` and
`Documents.collection(name)` through `Runtime.store(name)`. `CollectionImpl` and `DocumentImpl`
receive the `StoreRuntime` instead of a storage and a bus, and take their lock from
`store.lockFor(key)` at construction. The method bodies of `DocumentImpl` do not change.

**One bus per store.** All documents opened on the same store ID share one `ChangeBus`.
`flow()` keeps filtering by document key, so documents in different collections do not
cross-notify and a collector of one document only re-reads that document. The bus keeps the
`DROP_OLDEST` buffer of 64 from ADR-0002; it is per store, never global.

**Striped write locks.** A document's lock is chosen by hashing its key into a fixed array of
64 locks per store. The same key always maps to the same lock, so every instance of one
document excludes the others. Different documents normally land on different locks and never
wait on each other. Locks are created lazily, only when a document first needs its stripe, so
an app with ten documents creates at most ten.

**The guard is not on the hot path.** `lockFor` runs once per `DocumentImpl` in its
constructor. `get`, `set`, `update`, `delete`, `exists`, `readField`, and `writeField` use
the resolved lock directly and never touch `guard`. The guard is held only for a null check
and a possible lock creation.

**Startup is lazy and implicit.** `Runtime` is a Kotlin `object`, initialized on first
access (thread-safe on the JVM and, with the new memory manager, on Kotlin/Native). The first
public entry call runs `ensureInitialized()` (ADR-0012) before `Runtime.store(...)`, and
`platformStorage` runs only inside the `openStorage` lambda, so MMKV is always initialized
before a store is opened. Nothing happens at app startup, and a store that is never opened is
never created. There is no `close()` or shutdown; entries live until the process ends, like
the MMKV handles themselves.

**`inMemory()` stays isolated.** It builds a fresh, unregistered `StoreRuntime` per call, so
tests do not share buses or locks.

**No public API change.** Every type involved is `internal`. `checkKotlinAbi` should pass
without regenerating the dump.

## Consequences

**Positive**
- Any number of instances of the same document, opened anywhere, observe each other's writes
  and serialize their updates. Correctness no longer depends on the consumer holding a
  single instance.
- Documents in different collections stay isolated, and documents in the same store do not
  wait on each other except on a stripe collision.
- No new public surface, no lifecycle, and no cost at app startup.

**Negative / cost**
- **Stripe collisions.** Two unrelated documents that hash to the same stripe share a lock
  and can wait on each other. With 64 stripes the chance for any two given documents is about
  1.6%, and the wait is one MMKV write plus CBOR encoding. This is accepted as the price of a
  bounded, cleanup-free lock table. The stripe count is an internal constant and can change
  without affecting the public API or stored data.
- **Process-global state.** `Runtime` is shared by everything in the process, so tests that
  open real MMKV stores share buses and locks across tests, as they already share the MMKV
  files. If this causes trouble, an `internal` test-only reset will be added rather than a
  public one.
- **Lock ordering is by convention, not enforced.** No code path takes a second document
  lock while holding one, so striping cannot introduce a lock cycle. The one theoretical
  exception is a collector on `Dispatchers.Unconfined`, which `tryEmit` can resume inline on
  the writer's thread while that thread holds a document lock; if that collector then reads
  another document, a thread holding the other stripe and doing the same in reverse could
  deadlock. This already existed with per-instance locks and is unchanged by this ADR, but
  striping widens it slightly because unrelated documents can now share a lock.
- **Entries are never removed.** Store entries and stripe locks live for the process
  lifetime. Both are bounded (a handful of stores, at most 64 locks each).
- **Behavior change in the `Collection` contract.** The KDoc statement that documents from
  one collection share a change bus is widened to all instances opened on the same store
  name, including through separate `Documents.collection(name)` calls.

## Alternatives considered

- **Cache `Document` instances per `(store, key)`.** Rejected: two call sites with different
  `dispatcher` or `decorators` would silently get one configuration.
- **Documents register with the bus.** Rejected: registration implies unregistration, and a
  forgotten one leaks. Identity-keyed sharing needs no registry of documents.
- **One lock per store.** Rejected: unrelated documents would wait on each other, which is a
  stated requirement not to do.
- **One lock per document key in an unbounded map.** Rejected as the default: it grows with
  the number of distinct keys, which matters for apps that open one document per item ID (chat
  drafts, playback positions). Striping gives the same-document guarantee with a fixed bound.
- **Operation-counted lock entries (acquire and release per operation).** Bounded and
  collision-free, but it puts a shared `guard` acquisition on every `get`, `set`, and `update`
  of every document in the store. Rejected as more machinery than the problem warrants. It
  remains available as a later refinement if stripe collisions ever prove to matter.
- **Counting `DocumentImpl` instances or weak references.** Rejected: `Document` has no
  close or dispose method, adding one would change the public API, and common Kotlin has no
  `WeakReference`.
- **One global bus for the whole process.** Rejected: it would cross-notify across stores and
  make a single slow collector's buffer overflow affect every store.
