# Discussion: migrations

**Status:** Open for community feedback. Planned for v0.3.0. Not built, and no ADR yet.
**Started:** 2026-10-21

> This is a discussion doc, not a design doc. It records a conversation so we can pick it
> back up later without re-deriving it. When we decide to build it, promote the decided
> parts into an ADR and move the work into `roadmap.md` and `tasks.md`.
>
> It changes the shape of non-goal NG3 in the PRD ("`version` + `migrate {}` blocks"). The
> agreed design has no version in the public API. Update NG3 and the roadmap when this is
> built.

---

## 1. The prompt

What happens when a developer changes their data class after documents have been written, and
how much help should the library give?

## 2. What happens today

Measured with a throwaway test on the JVM host (CBOR encoding is the same on every platform):

| Change | Result |
|---|---|
| `Int ↔ Long ↔ Short ↔ Byte` | Reads the same value |
| `Float → Double` | Reads the same value |
| `T → T?`, and `T? → T` with a stored value | Reads the same value |
| `String ↔ enum` with a valid name | Reads the same value |
| `List<Int> → List<Long>`, `List → Set` | Reads the same value |
| Nested class gains a defaulted field, or loses a field | Works |
| `Int → Double`, `Int → Float`, `Double → Int`, `Double → Float` | Read fails |
| `Int ↔ String`, `Boolean ↔ Int` | Read fails |
| A stored `null` read as a non-null field | Read fails |
| An unknown enum name | Read fails |
| Nested class gains a required field, renames a field, or retypes a field | Read fails |
| `List<Int> → List<String>`, `Int → List<Int>`, `Map → List` | Read fails |
| `Long → Int`, out of range | **Silently wraps** (3_000_000_000 reads as -1294967296) |
| Rename a top-level field | The old value is ignored and the new field takes its default, with no error |

When a read fails, `get()` and `update { }` throw, and `flow()` ends with the exception. The
document is still writable (`set`, `update(prop, value)`, and field delegates for other
fields work), but a UI that depends on the read stops.

A related finding, from a second throwaway test: **a default is never stored**. It is applied
each time a missing field is read. With a computed default such as
`val createdAt: Long = nowMillis()`, three reads of an unwritten document returned three
different values (1, 2, 3), and nothing was stored until the next write (`update { it }`), after
which the value was stable. So a default is a placeholder, not a saved value, and changing a
default later changes the behavior for every user who never wrote that field.

## 3. What needs a developer migration

Anything the library can handle by its own rules needs no migration code.

| Situation | Who handles it |
|---|---|
| Add a field with a default or a nullable type | Automatic |
| Remove a field | Automatic (the old key is ignored and cleared on the next write) |
| Widen a number, add nullability, `String ↔ enum` with a valid name, widen list elements | Automatic |
| A nested class gains a defaulted field or loses a field | Automatic |
| Rename a field | Developer |
| Change a type incompatibly | Developer |
| Split, merge, or derive a field | Developer |
| Add a required field with no default | Not expected: every field has a default or is nullable (see Declaring Data Classes) |
| An unknown enum name | Developer |
| Change meaning without changing shape (pounds to kilograms) | Developer |

One automatic fix is still missing: narrowing out of range wraps silently. A range check would
turn it into a read failure. That is a separate, small change.

## 4. Constraints from the discussion

- The public API must be small and easy to learn.
- The developer never names or declares a version.
- No annotations.
- The type-safety problem: a removed field cannot be referenced from the class. So the new side
  of a migration uses real property references (`Profile::name`), and the old side can only use
  a name.

## 5. Ideas that were considered and set aside

- **A `version` declared on the document, with `migrate { }` blocks per version.** Rejected:
  the developer must name and maintain versions.
- **Keeping old classes (`NoteV1`, `NoteV2`) and chaining typed steps.** Safest, but the
  developer carries every old class forever.
- **Detecting the old shape from the data (unknown keys, unreadable fields) and running one
  `migrate { old, new -> }` block.** Small, but it cannot handle a change in meaning without a
  change in shape, such as pounds to kilograms.
- **`@FormerName("title")` on a property.** Rejected: no annotations.

## 6. The agreed shape

An ordered list of steps. Each step uses a few verbs. The version is the number of steps
applied, and it is internal.

```kotlin
val profile = Documents.document<Profile>("profile") {
    migrations {
        step {
            rename("title", to = Profile::name)
            convert<Int>("age", to = Profile::age) { it.toString() }
            drop("legacyFlag")
            fill(Profile::createdAt) { nowMillis() }
        }
        step {
            convert<Double>("weight", to = Profile::weight) { it * 0.4536 }
        }
        step {
            custom {
                old.getOrNull<String>("fullName")?.let {
                    new[Profile::firstName] = it.substringBefore(' ')
                    new[Profile::lastName] = it.substringAfter(' ', "")
                }
            }
        }
    }
}
```

### The verbs

- **`rename("old", to = Prop)`** moves a field. It does nothing if the old field is absent. It
  works on raw CBOR bytes, so it needs no type. It never overwrites a value that already exists
  under the new name: if both keys exist, it keeps the new value and removes the old key. This is
  a defensive rule, not a use case. Both keys can exist after an interrupted migration (the new key
  was written and the old one not yet removed), or when a step was added too late and a
  single-field write such as `update(Profile::name, ...)` created the new key while the stale old
  key was still there. The rule also makes the step safe to run twice.
- **`convert<Old>("name", to = Prop) { ... }`** reads the old value as `Old`, applies the lambda,
  and stores the result under `Prop`. It does nothing if the field is absent or is not an
  `Old`. The compiler checks the lambda's result against the type of `Prop`.
- **`drop("name")`** removes the field if it exists.
- **`fill(Prop) { ... }`** sets a field only if it has no stored value. See the next section.
- **`custom { ... }`** is the escape hatch for what the verbs cannot say, such as splits and
  merges. Inside it, `old.getOrNull<T>("name")` reads the old data and `new[Prop] = value`
  writes the new data.

Fields a step does not mention keep their value.

### Why `fill` stays

`fill` stores a value for an old document once, so the value no longer depends on the class's
default. It was kept after a discussion of its use cases. Under the rule that every field has a
default or is nullable, it is never needed for correctness. It has three uses:

1. **Pin a value before changing a default (grandfathering).** Defaults are not stored, so
   changing `val pageSize: Int = 20` to `50` moves every user who never wrote the field.
   `fill(Settings::pageSize) { 20 }` keeps existing users at 20, and new users get 50. This is
   the strongest case.
2. **A value computed once, then stable.** The class keeps a constant placeholder default
   (`createdAt: Long = 0`, `installId: String = ""`), and `fill(Profile::createdAt) { nowMillis() }`
   stores a real value. A computed default in the class does not work, because it changes on every
   read until the document is written.
3. **A stamp of when or where the document was upgraded**, such as the app version at the time.

Limits: the lambda takes no input, so a value derived from other fields needs `custom`. `fill`
never overwrites a stored value. The value is computed at migration time, so `createdAt` means
"when this document was migrated", not the true creation date.

Every use is also possible with `custom`, in a line or two. `fill` is kept for readability,
because "give old documents a starting value" is the most common migration.

### `getOrNull` inside `custom`

The proposed rule: `getOrNull<T>("name")` returns `null` when the field is absent, stored as
`null`, **or stored as a different type**. This matches the verbs, which skip a field that is not
of the expected old type, and it lets a block recognize the old shape. It reverses an earlier
suggestion that a type mismatch should throw. To be confirmed when this is built.

## 7. How the version works internally

- The library stores the number of applied steps in a hidden marker next to the document's
  fields.
- A document written by the current code is stamped with the full step count.
- A document with no stamp counts as "before all steps". That is correct for every document
  written before migrations were declared.
- On first access, only the missing steps run. A document stamped 1 with 2 steps declared runs
  step 2 only.
- The migrated fields and the new stamp are written together, with the stamp last. Because each
  verb does nothing when its source is missing, an interrupted step can run again.
- The check is one hidden key, so it runs for every access, including field delegates. It happens
  once per document per process, tracked in the store runtime
  ([ADR-0022](../adr/0022-process-wide-store-runtime.md)).
- If the stamp is higher than the step count (an older app reading newer data), the document is
  left alone.

## 7a. When a step fails

Decided: a failing migration leaves the document **untouched** and throws a new public
**`DocumentMigrationException`**.

How a step can fail (none of these can be caught by the compiler, because they depend on stored
data):

1. A developer lambda throws, for example `it.toInt()` on a stored `"abc"`.
2. A developer lambda rejects a value, for example `Theme.valueOf("BLUE")`.
3. A `custom` block assumes a shape, for example `it.split(' ')[1]` on a name with no space.
4. A `FieldDecorator` cannot unwrap an old field, for example after the key was lost.
5. After all steps, the document still cannot be read as the current class. This catches a step
   the developer forgot to write.
6. A storage error while writing.

What the compiler does check: that the target property exists, that a `convert` lambda returns
the type of its target property, and the type of the lambda's input.

How it works:

- All pending steps run in memory against the stored fields.
- The result is checked by decoding it as the current class.
- Only if all of that succeeds are the new fields and the new stamp written, stamp last.
- If anything throws, nothing is written and the stamp does not move. The document stays in its
  old, intact shape, and the step runs again after the developer fixes it in a new release.

Rejected: skipping the failing step and continuing. The stamp would advance over a half-migrated
document, later steps would run on data they did not expect, and the damage could not be
repaired afterward.

`DocumentMigrationException` carries the `documentKey`, the position of the step, the `field`
when one is involved (nullable), and the `cause`. Its constructor is `internal`. A message looks
like this:

> Migration step 2 of document 'profile' failed while converting field 'age'
> (NumberFormatException). The document was not changed.

Like the decoding messages ([ADR-0023](../adr/0023-decoding-failure-messages.md)), it names the
cause's exception class and never its text, so a stored value cannot reach a log.

This adds one public type to the migrations surface: `migrations { }`, a builder, the step scope,
and `DocumentMigrationException`. A separate type was chosen over reusing
`DocumentDecodingException` because a migration failure is not a decoding failure, and a caller may
want to catch the two differently.

A documentation note is needed: a typo in an old field name does not fail. Each verb does nothing
when its source field is absent, so `rename("titel", to = Profile::name)` silently does nothing.
The end check catches it only when the field it should have produced is required.

## 8. Rules for the developer

These were accepted: the developer is responsible for them.

1. **Steps are append-only.** The library counts by position, so a step that has shipped is never
   edited, reordered, or deleted.
2. **Write the step in the same release as the class change.** If a release changes the class with
   no step, a later release adds the step, and documents written in between are in the new shape
   without a stamp. A step such as the pounds-to-kilograms conversion would then apply twice.

## 9. Things the implementation must get right

- **Decorators.** `FieldDecorator` ties each value to its field name (for example as associated
  data in AES-GCM). `rename` and `convert` must unwrap with the old field name and wrap with the
  new one. A plain byte copy would leave an unreadable value.
- **Hidden marker.** It must not count as a field: `exists()`, `delete()`, `set()`, and the
  stale-key clearing must all treat it correctly, and a document must never be stamped with no
  fields.
- **Locking.** The steps run under the document's write lock, in one operation with the stamp.
- **Messages.** A failing step should say which step (by position) and which field.

## 10. Open items

- Consider a short warning on the Declaring Data Classes page: do not use a computed default
  such as `nowMillis()`, because it changes on every read until the document is written.
- Confirm the `getOrNull` mismatch rule above.
- Add the narrowing range check as its own change.
- Write the ADR, update NG3 in the PRD and the roadmap, then plan the tasks.
