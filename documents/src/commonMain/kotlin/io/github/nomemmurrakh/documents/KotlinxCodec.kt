package io.github.nomemmurrakh.documents

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Default [Codec] backed by kotlinx.serialization.
 *
 * Each instance serializes values of a single field type [T]: the field's [KSerializer] is
 * supplied at construction, and [json] controls the on-disk JSON format. A field whose type is
 * itself `@Serializable` is encoded as a serialized sub-blob under its single key.
 *
 * @param serializer the serializer for the field value type [T].
 * @param json the JSON format used to encode and decode; defaults to a standard [Json].
 */
public class KotlinxCodec<T>(
    private val serializer: KSerializer<T>,
    private val json: Json = Json,
) : Codec<T> {

    override fun encode(value: T): ByteArray =
        json.encodeToString(serializer, value).encodeToByteArray()

    override fun decode(bytes: ByteArray, deserializer: KSerializer<T>): T =
        json.decodeFromString(deserializer, bytes.decodeToString())
}
