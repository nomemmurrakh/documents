# ADR-0023: Distinct messages for each cause of a decoding failure

**Status:** Accepted (amends [0009](0009-document-decoding-exception.md))
**Date:** 2026-10-20

## Context

ADR-0009 made every read failure a `DocumentDecodingException` that names the document key,
the field, and the underlying cause. The message, however, was the same for every cause:

> Failed to decode field 'title' of document 'note'

Four different situations produce it, and a caller cannot tell them apart from the text. The only
difference is in `cause`, which is `null` for two of them:

| | Cause | `cause` | Raised in |
|---|---|---|---|
| A | `update()` on a never-written document, where a field has no default and is not nullable | `null` | the decoder, while decoding against empty storage |
| B | A stored document is missing a required field, for example after a field was added to the data class | `null` | the decoder |
| C | A field's stored bytes are corrupt, or are the wrong type for the field | a serialization exception | the decoder and `readField` |
| D | A `FieldDecorator.unwrap` fails | the decorator's exception | the decoder and `readField` |

Case A is the most common surprise for new users, and it looks identical to real corruption. The
Troubleshooting page already has to apologize for it. In `readField`, a decorator failure and a
decode failure share one `try` block, so they cannot be told apart there at all.

The roadmap item is "Rich error messages to differentiate causes".

## Decision

Keep one public exception type, `DocumentDecodingException`, and give each cause its own message.
The public API does not change: the constructor stays `internal`, and `documentKey`, `field`, and
`cause` keep their meaning. The messages are:

**A. `update()` on a never-written document**

> Cannot update document 'note' because it has not been written yet and field 'title' has no
> default value. Give 'title' a default in the constructor (`val title: String = ...`), make it
> nullable (`val title: String?`), or call `set()` with a full value before `update()`.

**B. A stored document is missing a required field**

> Document 'note' is missing a stored value for required field 'title'. The data may have been
> written by a different version of your data class. Give 'title' a default value or make it
> nullable so that older data still loads.

**C. Corrupt or incompatible bytes**

> Field 'title' of document 'note' could not be read as kotlin.String (SerializationException).
> The stored data may be corrupted or may have been written by an incompatible version of your
> data class.

**D. A field decorator failed**

> A field decorator failed to unwrap field 'authToken' of document 'session'
> (IllegalArgumentException). Check that the decorators, their order, and any keys match the ones
> used when the value was written.

The example in message A uses the field's real name and type. The expected type in message C comes
from the field's serializer descriptor.

**Telling A from B.** `defaults()`, which builds the starting value for `update()`, decodes
against `EmptyStorage`, the object that stands for "no document". The decoder recognizes it, so a
missing required field there is case A. A normal read decodes against real storage, so a missing
required field means a partly written document, which is case B. A fully absent document still
returns `null` from `get()` and never reaches the decoder.

**Telling C from D.** `readField` unwraps and decodes in two separate steps, each with its own
catch, so a decorator failure is reported as D and a decode failure as C, the same as in the
document decoder.

**What goes in a message.** Messages include the document key, the field name, the expected type,
and the cause's exception class name. They do not include the cause's own text. A decoder error can
quote stored bytes, and a stored value such as part of a token must not reach a log through a
message. The full cause stays chained on the exception for anyone who needs it.

The previous general message, "Failed to decode document 'x'", stays as the fallback for a failure
with no single field. Nothing raises that case today.

## Consequences

**Positive**
- A reader can tell from the message alone whether the data class is missing a default, an older
  document is missing a field, the bytes are damaged, or a decorator is misconfigured, and each
  message says how to fix it.
- No public API change, so `checkKotlinAbi` is unaffected and no migration is needed.
- Case A, the most common first-run crash, now explains itself.

**Negative / cost**
- **Message text is not an API.** Callers who need to branch on the cause still have only the
  message and `cause` to look at. A `reason` property was considered and left out, because it
  would add a public enum to maintain. It can be added later without breaking anything.
- **C cannot separate corruption from a changed field type.** Both look the same to the decoder,
  so the message says "corrupted or incompatible".
- **Message wording is covered by tests only for its key phrases**, so rewording later does not
  break tests that were checking whole strings.

## Alternatives considered

- **A public `reason` property on the exception.** Rejected for now: it adds an enum to the public
  surface and the ABI dump for a benefit the roadmap item did not ask for.
- **Subclasses of `DocumentDecodingException` per cause.** Rejected: the largest public surface
  and the hardest to evolve in a 0.x library.
- **Including the cause's message in the text.** Rejected because a decoder error can quote stored
  data, which could leak sensitive values into logs.
