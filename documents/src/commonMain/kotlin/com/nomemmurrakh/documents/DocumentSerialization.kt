package com.nomemmurrakh.documents

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.cbor.Cbor

@OptIn(ExperimentalSerializationApi::class)
internal fun <T> encodeDocument(
    documentKey: String,
    value: T,
    serializer: SerializationStrategy<T>,
    storage: Storage,
    cbor: Cbor,
    decorators: List<FieldDecorator> = emptyList(),
) {
    serializer.serialize(DocumentEncoder(documentKey, storage, cbor, decorators), value)
}

@OptIn(ExperimentalSerializationApi::class)
internal fun <T> decodeDocument(
    documentKey: String,
    deserializer: DeserializationStrategy<T>,
    storage: Storage,
    cbor: Cbor,
    decorators: List<FieldDecorator> = emptyList(),
): T = deserializer.deserialize(DocumentDecoder(documentKey, storage, cbor, decorators))
