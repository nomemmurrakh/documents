# Discussion — MMKV built-in encryption passthrough

**Status:** Exploratory, dormant — no decision made, no ADR yet, nothing scheduled in
`tasks.md`.
**Started:** 2026-07-04

> This is a discussion doc, not a design doc. It records a possible future direction
> separately from the active `FieldDecorator` extension-point work
> (`docs/discussions/decorators-and-encryption.md`). This idea is not currently being
> pursued — it's kept here in case a simple, built-in, default encryption feature (as
> opposed to a general-purpose decorator mechanism) becomes worth doing later.

---

## 1. The idea

Ship encryption as a library-authored, built-in feature — not a general extension point —
by passing an optional key straight through to MMKV's own encryption support. `Storage`
stays `internal`; no public decorator interface is needed for this to work.

```kotlin
val store = Documents.collection("secure") {
    encrypted(key = ...) // config flag; wires up an internal decorator
}
```

- Purely additive — new config DSL entry, no existing call compiles differently.
- No ABI commitment beyond a new optional config field.
- Limitation: no user-authored decorators (custom logging, custom caching, per-field
  targeting) — only whatever the library builds in. This is the tradeoff against the
  `FieldDecorator` design in the other doc, which is more general but a larger surface.

## 2. Why passthrough, not a hand-rolled cipher

Owning encryption ourselves means owning key handling, cipher choice, correctness, and
performance for every consumer, forever — a real liability for hand-rolled crypto.

But MMKV already has encryption built in (this is what `roadmap.md`'s v2.0 entry and PRD
NG2's parenthetical — "MMKV supports it" — point at). So this doesn't mean writing a
cipher; it means a thin passthrough: an optional key on `collection {}` that flows straight
into MMKV's own instance-open call. MMKV does the actual encryption.

Given NG2 already points specifically at MMKV's own support, the passthrough is the smaller
and safer way to satisfy it, if/when this is picked up.

## 3. What MMKV actually exposes (researched 2026-07-04)

Repo currently on `com.tencent:mmkv` **2.4.0**. Current opens are unencrypted, 2-arg calls:

- Android — `documents/src/androidMain/.../PlatformStorage.android.kt` —
  `MMKV.mmkvWithID(name, MMKV.SINGLE_PROCESS_MODE)`
- iOS — `documents/src/iosMain/.../PlatformStorage.ios.kt` —
  `MMKV.mmkvWithID(name, mode = MMKVSingleProcess)`

No `cryptKey`/`reKey`/encryption references exist anywhere in code or `docs/adr/` today —
this would be new ground, no ADR governs it yet.

### Android

```java
MMKV.mmkvWithID(mmapID, mode, cryptKey: String?, aes256: Boolean = false)
// + reKey to rotate or remove on an already-open instance:
mmkv.reKey(cryptKey: String?, aes256: Boolean = false): Boolean
```

Key type is a nullable `String`; `null` = unencrypted.

### iOS (via cinterop)

```objc
+ (instancetype)mmkvWithID:(NSString *)mmapID cryptKey:(NSData *)cryptKey;
[kv reKey:key];
[kv reKey:key aes256:YES];
[kv reKey:nil];              // remove encryption
```

Key type is `NSData` — **not** `String`. This is the one real cross-platform asymmetry.
v2.4.0+ also has an `MMKVConfig` struct with a `cryptKey: NSData?` field as an alternate
constructor path.

### Constraints & behavior

- **Algorithm**: AES CFB-128 by default; AES CFB-256 if `aes256 = true`. No documented hard
  key-length ceiling beyond that — longer keys are hashed/derived to fit.
- **Encoding asymmetry**: Android takes `String` (UTF-8 internally); iOS takes raw `NSData`.
  A `commonMain` API should take the key as `ByteArray` and convert per platform — the repo
  already has a `ByteArray.toNSData()` helper in `MmkvStorage.ios.kt` usable for this.
- **Rotation**: `reKey(...)` works on an already-open instance in either direction
  (encrypted→different key, encrypted→plain, plain→encrypted). Not fixed at open-time only.
- **Wrong key / mismatched open**: not silent corruption — MMKV's CRC/checksum check fails
  and it discards/attempts recovery, the same path as ordinary corruption handling. Recovery
  is best-effort, not guaranteed.
- **Cross-platform note**: same algorithm family on both platforms, but different crypto
  libraries underneath (own implementation on Android, OpenSSL on iOS/macOS). Cross-platform
  compatibility of an encrypted store (same key bytes, opened from both platforms) should
  hold but is **not verified by this repo's test suite today**.
- A historical MMKV bug caused corruption on an encrypted instance holding only a single
  key-value pair; understood to be fixed upstream well before 2.4.0, but worth confirming
  against MMKV's changelog before shipping.

### Bottom line

The thin-passthrough story is clean. Both platforms take the key at open time via an extra
parameter, and both support later rotation via `reKey`. The only real friction is the
`String` vs. `NSData` key-type mismatch, which a `ByteArray`-typed `commonMain` config field
absorbs with one conversion per platform.

## 4. Open items, if this is ever picked up

- [ ] Decide whether to pursue this as a built-in feature alongside (or instead of) using
      the `FieldDecorator` mechanism to build encryption on top of it — see
      `docs/discussions/decorators-and-encryption.md`.
- [ ] Promote NG2 out of non-goal status in `PRD.md`.
- [ ] Add the work to `roadmap.md` (already slotted under v2.0) and `tasks.md`.
- [ ] Write an ADR covering: key storage/rotation policy, the `ByteArray`→`String`/`NSData`
      conversion, and whether `reKey` is exposed to consumers or encryption is fixed at
      collection-creation time only.
