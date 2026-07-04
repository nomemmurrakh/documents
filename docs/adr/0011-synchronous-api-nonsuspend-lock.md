# ADR-0011: Synchronous document API, non-suspending write lock, dispatcher governs flow collection

**Status:** Accepted
**Date:** 2026-06-21

## Context

Phase 8 opens with T8.1 (dispatcher configuration, "default `Dispatchers.IO`") and T8.2
(per-document write mutex for atomic multi-field `UPDATE`). Three locked documents speak to
concurrency, and as written they do not fit together:

- **api-design §3 / §10** show every `Document` operation as **synchronous, non-suspend**:
  `val current: User? = user.get()`, `user.set(...)`, `user.delete()`, `user.exists()`. The
  field delegates (§5) are `ReadWriteProperty`, whose `getValue`/`setValue` cannot be `suspend`.
- **api-design §8** says "suspend operations run on `Dispatchers.IO` by default (configurable)"
  and "`flow()` is safe to collect from any dispatcher; emissions are conflated."
- **architecture §9** says writes are serialized per document via a "lightweight per-key mutex"
  to keep multi-field `set(UPDATE)` atomic.

The contradiction: a synchronous API has **no suspend operations** for a dispatcher to govern,
and `kotlinx.coroutines.Mutex.withLock` is itself a `suspend` call that cannot be invoked from a
non-suspend `set()`. Something has to give.

The decisive fact is the storage substrate. MMKV is an `mmap`-backed key-value store: a
`get`/`set` is a memcpy into a memory-mapped page, flushed to disk asynchronously by the kernel.
There is no blocking syscall on the hot path (no `fsync`, socket, or query round-trip).
architecture §8 already states the library "adds no extra I/O — writes hit MMKV's memory-mapped
file exactly as raw MMKV would." The per-operation cost is **CPU-bound** (descriptor walking +
JSON encode/decode of small field values), not I/O-bound.

Making the whole API `suspend` was considered and rejected (see Alternatives): it would break
the locked synchronous surface at every call site, and — fatally — the field delegates (§5, a
headline feature) cannot be `suspend`, so `readField`/`writeField` must remain synchronous
regardless, fracturing the API into a suspend document path and a non-suspend delegate path.

## Decision

Keep the documented surface **synchronous** and reconcile §8/architecture §9 to it:

1. **Document operations stay non-suspend**, exactly as api-design §3/§10 show. No `suspend`
   variants in v1.

2. **T8.2 — per-document write serialization uses a non-suspending reentrant lock**, not
   `kotlinx.coroutines.Mutex`. The project already uses `kotlinx.atomicfu.locks.reentrantLock` +
   `withLock` (in `InMemoryStorage`); the same primitive serializes the clear-then-encode write
   sequence so a concurrent reader's `get()` never observes a half-applied multi-field `UPDATE`.
   The lock must be **reentrant**: `set(UPDATE)` does a read-modify-write (`get()` then
   `set(value)`) that must hold a single lock across the whole sequence, and `set(value)`
   re-acquires the same lock — atomicfu's `reentrantLock()` is reentrant, so this composes. The
   change bus still emits the document key only **after** the write commits (preserving the
   T6.1 emit-after-commit invariant and architecture §9's "emits only after all field writes
   for an operation commit").

3. **T8.1 — the configurable dispatcher governs `flow()`/`stateFlow()` collection**, the only
   place a dispatcher can meaningfully apply when no operation blocks. `DocumentsConfig` gains a
   `dispatcher` (default `Dispatchers.Default`, see below), threaded to each document and applied
   to the reactive streams via `flowOn`. Synchronous `get`/`set`/`delete`/`exists` and the field
   delegates do not touch it.

4. **Default dispatcher is `Dispatchers.Default`, not `Dispatchers.IO`.** Since the work is
   CPU-bound (JSON encode/decode), `Default` is the correct pool; `IO` is for blocking I/O the
   library does not perform. api-design §8 is corrected accordingly.

api-design §8 is updated to: operations are synchronous and non-blocking (memory-mapped);
per-document writes are serialized by a non-suspending reentrant lock; the configurable
dispatcher applies to `flow()`/`stateFlow()` collection (default `Dispatchers.Default`).
architecture §9's "mutex" is read as "mutual exclusion" (the non-suspending lock), not
specifically `coroutines.Mutex`.

## Consequences

**Positive**
- The locked synchronous surface (§3/§10) is honored unchanged; no call site churns; the field
  delegates keep working on the same synchronous write path as `set()`.
- One concurrency primitive already in the codebase (atomicfu `reentrantLock`); no new dependency,
  no `coroutines.Mutex`, no `runBlocking`.
- Reentrancy makes `set(UPDATE)`'s read-modify-write atomic without restructuring the
  delegation `set(strategy, builder)` → `set(value)`.
- The dispatcher default (`Default`) matches the actual CPU-bound cost profile.

**Negative / cost**
- Deviates from the literal §8 text ("suspend operations", "Dispatchers.IO"). Mitigated by this
  ADR and a §8 correction — the source of truth is updated, not silently diverged from (same
  pattern as ADR-0008/0010).
- A non-suspending lock does not observe coroutine cancellation. Acceptable: critical sections
  are tiny in-memory encode/decode steps that complete promptly; there is nothing long-running
  to cancel.
- No automatic off-main-thread execution for a pathologically large object's encode. Acceptable
  for v1 (rare); a caller can wrap a synchronous call in `withContext` themselves, and a future
  real-I/O backend would justify revisiting (see below).

## Alternatives considered

- **Make all operations `suspend` (`withContext(io)` + `Mutex.withLock`)** — matches §8
  literally and enables cancellation, but breaks the synchronous surface at every call site
  (`val u = user.get()` → `scope.launch { user.get() }`), targets the wrong pool (`IO` for
  CPU-bound work), and cannot cover the field delegates (`ReadWriteProperty` is never `suspend`),
  forcing a split suspend/sync API. Disproportionate and self-contradictory for a memory-mapped
  settings store. Rejected for v1. A future backend doing real blocking I/O (network sync,
  SQLite, cross-process) would flip this trade and justify a suspend API then.
- **`coroutines.Mutex` with `runBlocking` inside sync `set()`** — bridges suspend lock into a
  sync method but `runBlocking` on Android main thread is exactly what the dispatcher story is
  meant to avoid, and it is unavailable/ill-advised on Native. Rejected.
- **No lock; rely on MMKV/storage atomicity** — MMKV is thread-safe per key, but multi-field
  `UPDATE` spans several key writes; without serialization a concurrent reader can observe a
  half-applied update (violates test-plan §8). Rejected.
