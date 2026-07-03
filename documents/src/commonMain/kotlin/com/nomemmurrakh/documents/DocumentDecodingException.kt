package com.nomemmurrakh.documents

/**
 * Thrown when a document cannot be decoded from storage.
 *
 * A document is stored as one key per field, so a read can fail on a single field while the
 * rest are intact. This exception names the [documentKey] and, when one field is implicated,
 * the [field], and attaches the underlying [cause]. It is raised instead of a bare
 * `SerializationException` so that callers never have to depend on the serialization layer's
 * error types.
 *
 * @property documentKey the key of the document whose decode failed.
 * @property field the field whose stored value could not be decoded, or `null` when no single
 *   field is implicated.
 */
public class DocumentDecodingException internal constructor(
    public val documentKey: String,
    public val field: String?,
    cause: Throwable?,
) : RuntimeException(messageFor(documentKey, field), cause)

private fun messageFor(documentKey: String, field: String?): String =
    if (field == null) {
        "Failed to decode document '$documentKey'"
    } else {
        "Failed to decode field '$field' of document '$documentKey'"
    }
