package com.nomemmurrakh.documents

internal fun missingFieldOnNewDocument(
    documentKey: String,
    field: String,
    typeName: String,
): DocumentDecodingException {
    val type = typeName.substringAfterLast('.')
    return DocumentDecodingException(
        documentKey,
        field,
        cause = null,
        detail = "Cannot update document '$documentKey' because it has not been written yet and " +
            "field '$field' has no default value. Give '$field' a default in the constructor " +
            "(`val $field: $type = ...`), make it nullable (`val $field: $type?`), or call `set()` " +
            "with a full value before `update()`.",
    )
}

internal fun missingFieldOnStoredDocument(documentKey: String, field: String): DocumentDecodingException =
    DocumentDecodingException(
        documentKey,
        field,
        cause = null,
        detail = "Document '$documentKey' is missing a stored value for required field '$field'. " +
            "The data may have been written by a different version of your data class. " +
            "Give '$field' a default value or make it nullable so that older data still loads.",
    )

internal fun unreadableField(
    documentKey: String,
    field: String,
    typeName: String,
    cause: Throwable,
): DocumentDecodingException =
    DocumentDecodingException(
        documentKey,
        field,
        cause,
        detail = "Field '$field' of document '$documentKey' could not be read as $typeName " +
            "(${cause.shortName()}). The stored data may be corrupted or may have been written by " +
            "an incompatible version of your data class.",
    )

internal fun decoratorFailed(documentKey: String, field: String, cause: Throwable): DocumentDecodingException =
    DocumentDecodingException(
        documentKey,
        field,
        cause,
        detail = "A field decorator failed to unwrap field '$field' of document '$documentKey' " +
            "(${cause.shortName()}). Check that the decorators, their order, and any keys match " +
            "the ones used when the value was written.",
    )

private fun Throwable.shortName(): String = this::class.simpleName ?: "Throwable"
