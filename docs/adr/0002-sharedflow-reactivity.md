# ADR-0002: In-memory SharedFlow change bus for reactivity

**Status:** Accepted
**Date:** 2026-06-15

## Context

The library must notify observers when a document or field changes (`flow`, `stateFlow`,
`fieldFlow`). MMKV provides no native change-listener mechanism.

## Decision

Maintain a process-local `MutableSharedFlow<String>` (the affected document key) as a change
bus. After every committed write or delete, emit the key. `flow()` filters by key, re-reads
the value, and emits it (conflated).

## Consequences

**Positive**
- Idiomatic Kotlin: observers get cold `Flow` / hot `StateFlow` for free.
- Zero added storage cost; reactivity is purely in-memory.
- Emissions occur only after durable commit, so observers never see uncommitted state.

**Negative / cost**
- Notifications are **process-local**. A write from another process (MMKV multi-process
  mode) will not trigger emission in this process. This is a documented v1 limitation (NG4).
- Requires care that emission happens exactly once per logical operation, after all field
  writes commit.

## Alternatives considered

- **Polling MMKV** — wasteful and laggy. Rejected.
- **File-watch on MMKV's mmap file** — platform-specific, fragile, and still racy.
  Rejected for v1.
