package io.github.nomemmurrakh.documents

import kotlinx.serialization.KSerializer

/**
 * Converts a single field value to and from the bytes persisted in [Storage].
 *
 * A [Codec] is the boundary between typed field values and the raw byte arrays stored under
 * each `{document}::{field}` key. The default implementation serializes with
 * kotlinx.serialization; supply a custom implementation per store or per document to change
 * the on-disk format.
 *
 * @param T the field value type handled by this codec.
 */
public interface Codec<T> {

    /**
     * Encodes [value] into the byte array to persist.
     */
    public fun encode(value: T): ByteArray

    /**
     * Decodes [bytes] previously produced by [encode] back into a value of type [T], using
     * [deserializer] to interpret the bytes.
     */
    public fun decode(bytes: ByteArray, deserializer: KSerializer<T>): T
}
