package io.github.nomemmurrakh.documents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal class DocumentEncoder(
    private val documentKey: String,
    private val storage: Storage,
    private val cbor: Cbor,
) : AbstractEncoder() {

    override val serializersModule: SerializersModule = cbor.serializersModule

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
        FieldCompositeEncoder(documentKey, storage, cbor)

    private class FieldCompositeEncoder(
        private val documentKey: String,
        private val storage: Storage,
        private val cbor: Cbor,
    ) : CompositeEncoder {

        override val serializersModule: SerializersModule = cbor.serializersModule

        private fun <T> put(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T) {
            val key = Keys.field(documentKey, descriptor.getElementName(index))
            storage.putBytes(key, cbor.encodeToByteArray(serializer, value))
        }

        override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean): Unit =
            put(descriptor, index, Boolean.serializer(), value)

        override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte): Unit =
            put(descriptor, index, Byte.serializer(), value)

        override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char): Unit =
            put(descriptor, index, Char.serializer(), value)

        override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short): Unit =
            put(descriptor, index, Short.serializer(), value)

        override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int): Unit =
            put(descriptor, index, Int.serializer(), value)

        override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long): Unit =
            put(descriptor, index, Long.serializer(), value)

        override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float): Unit =
            put(descriptor, index, Float.serializer(), value)

        override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double): Unit =
            put(descriptor, index, Double.serializer(), value)

        override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String): Unit =
            put(descriptor, index, String.serializer(), value)

        override fun <T> encodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T,
        ): Unit = put(descriptor, index, serializer, value)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> encodeNullableSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T?,
        ) {
            val key = Keys.field(documentKey, descriptor.getElementName(index))
            val nullableSerializer = (serializer as KSerializer<T>).nullable
            storage.putBytes(key, cbor.encodeToByteArray(nullableSerializer, value))
        }

        override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
            val field = descriptor.getElementName(index)
            throw UnsupportedOperationException(
                "Inline value class field '$documentKey$field' is not supported by Documents v1",
            )
        }

        override fun endStructure(descriptor: SerialDescriptor) {}
    }
}
