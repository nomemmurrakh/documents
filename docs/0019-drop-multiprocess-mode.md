# ADR-0019: Drop `multiProcess`; storage is always single-process

**Status:** Accepted
**Date:** 2026-07-03

## Context

`CollectionConfig.multiProcess` opened the backing MMKV store in `MULTI_PROCESS_MODE`
(Android) / `MMKVMultiProcess` (iOS) when set, making the store's mmap-backed storage safe
to read and write from more than one OS process concurrently.

But this library's reactivity is entirely in-process: `Document.flow()`/`stateFlow()` are
driven by a `ChangeBus` (a `MutableSharedFlow`) that only ever receives an `emit(key)` from
writes made through the same `CollectionImpl` instance (ADR-0002). A write made by another OS
process through its own `CollectionImpl` never reaches this process's `ChangeBus`. The result:
`multiProcess = true` made concurrent access to the store *safe*, but gave no indication that
`flow()` would silently miss writes from other processes — a correctness trap for exactly the
use case the flag advertised.

Bridging that gap was considered and researched before this decision:

- MMKV's own cross-process signal (`registerContentChangeNotify`/`checkContentChangedByOuterProcess`
  on Android, `MMKVHandler.onMMKVContentChange` on iOS) is pull-based, store-granularity only (no
  per-key information), and iOS's own docs state it gives no real-time guarantee.
- A custom per-key notification layered on top (e.g. an in-store changelog of `(key, counter)`
  entries) is possible but adds a second write per mutation for every multi-process collection,
  needs a ring-buffer/overflow fallback that degrades back to store-granularity anyway, and
  requires a process-wide `mmapID -> ChangeBus` registry that this library has no lifecycle
  (`close()`/`dispose()`) to safely evict from — introducing a memory-leak risk to fix a
  reactivity gap.
- Prior art points the same direction: the most-used community MMKV wrapper does not attempt
  cross-process reactive flows at all; Android deprecated multi-process `SharedPreferences`
  because it "never worked reliably"; and Google's own purpose-built `MultiProcessDataStore`
  (`InterProcessCoordinator`) has shipped cross-process dispatch bugs despite being a dedicated
  effort at solving exactly this. Reliable cross-process *reactive* notification is a hard,
  unsolved-in-general problem, not an oversight in MMKV.

Given that, offering `multiProcess` as a config knob implies a level of support (safe storage
*and* reactive observation across processes) that this library cannot actually deliver on the
reactive half, on either platform.

## Decision

Remove `multiProcess` from the public surface. Storage is always opened single-process
(`MMKV.SINGLE_PROCESS_MODE` / `MMKVSingleProcess`):

```kotlin
internal expect fun platformStorage(name: String): Storage
```

`CollectionConfig` retains only `dispatcher`. Concurrent access to the same MMKV store from more
than one OS process is unsupported and can corrupt the store; this is documented on `Collection`
and in the README rather than half-supported behind a flag.

## Consequences

**Positive**
- No config option implies a reactivity guarantee (cross-process live `flow()`) the library
  cannot provide.
- Smaller public surface: `CollectionConfig` drops to one property.
- Removes a footgun where `multiProcess = true` made storage safe to share but left `flow()`
  silently blind to writes from other processes.

**Negative / cost**
- **Breaking API change** (pre-1.0): `CollectionConfig.multiProcess` no longer exists; any call
  site setting it fails to compile. No behavior change for existing single-process callers
  (the default was already `false`).
- Apps that need a store shared across processes (a background service and the UI process on
  Android; a host app and an app extension on iOS) cannot use this library for that store today.

## Alternatives considered

- **Keep `multiProcess`, document the reactivity gap.** Leaves a correctness trap in the public
  API surface — the flag reads as "this store supports multi-process use," not "this store is
  multi-process-safe but not multi-process-reactive." Rejected as misleading by omission.
- **Build per-key cross-process notification** (in-store changelog + registry, see Context).
  Rejected: the write-amplification and registry-lifecycle costs are real and permanent, paid by
  every multi-process collection, to approximate a guarantee (low-latency cross-process reactive
  flow) that even dedicated prior art (Jetpack DataStore) has struggled to deliver reliably.
- **Coarse store-level re-check** (subscribe to MMKV's notify, re-read and diff every open
  document in that store on signal). Still leaves an explicit, silently-non-real-time reactivity
  contract that differs from same-process `flow()` behavior, without the safety net of the store
  itself being off the table. Rejected as complexity without a corresponding reliability payoff,
  consistent with the prior-art findings above.
