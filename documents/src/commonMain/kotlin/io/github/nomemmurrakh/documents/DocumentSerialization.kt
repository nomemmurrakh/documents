package io.github.nomemmurrakh.documents

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

internal fun <T> encodeDocument(
    documentKey: String,
    value: T,
    serializer: SerializationStrategy<T>,
    storage: Storage,
    json: Json,
) {
    serializer.serialize(DocumentEncoder(documentKey, storage, json), value)
}

internal fun <T> decodeDocument(
    documentKey: String,
    deserializer: DeserializationStrategy<T>,
    storage: Storage,
    json: Json,
): T = deserializer.deserialize(DocumentDecoder(documentKey, storage, json))
