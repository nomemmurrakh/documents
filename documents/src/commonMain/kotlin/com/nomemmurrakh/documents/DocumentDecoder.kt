package com.nomemmurrakh.documents

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

private const val CBOR_NULL: Byte = 0xF6.toByte()

@OptIn(ExperimentalSerializationApi::class)
internal class DocumentDecoder(
    private val documentKey: String,
    private val storage: Storage,
    private val cbor: Cbor,
    private val decorators: List<FieldDecorator> = emptyList(),
) : AbstractDecoder() {

    override val serializersModule: SerializersModule = cbor.serializersModule

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        FieldCompositeDecoder(documentKey, storage, cbor, decorators)

    @Suppress("TooManyFunctions") // implements the full CompositeDecoder SPI
    private class FieldCompositeDecoder(
        private val documentKey: String,
        private val storage: Storage,
        private val cbor: Cbor,
        private val decorators: List<FieldDecorator>,
    ) : CompositeDecoder {

        override val serializersModule: SerializersModule = cbor.serializersModule

        private var nextIndex = 0

        @Suppress("ThrowsCount") // one rethrow per caught exception type, per the FieldDecorator failure contract
        private fun bytes(descriptor: SerialDescriptor, index: Int): ByteArray? {
            val fieldName = descriptor.getElementName(index)
            val raw = storage.getBytes(Keys.field(documentKey, fieldName)) ?: return null
            return try {
                applyUnwrap(decorators, fieldName, raw)
            } catch (cause: SerializationException) {
                throw DocumentDecodingException(documentKey, fieldName, cause)
            } catch (cause: IllegalStateException) {
                throw DocumentDecodingException(documentKey, fieldName, cause)
            } catch (cause: IllegalArgumentException) {
                throw DocumentDecodingException(documentKey, fieldName, cause)
            }
        }

        private fun <T> read(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>): T {
            val field = descriptor.getElementName(index)
            val raw = bytes(descriptor, index)
                ?: throw DocumentDecodingException(
                    documentKey,
                    field,
                    cause = null,
                )
            return decode(field, deserializer, raw)
        }

        private fun <T> decode(field: String, deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
            try {
                cbor.decodeFromByteArray(deserializer, bytes)
            } catch (cause: SerializationException) {
                throw DocumentDecodingException(documentKey, field, cause)
            } catch (cause: IllegalStateException) {
                throw DocumentDecodingException(documentKey, field, cause)
            } catch (cause: IllegalArgumentException) {
                throw DocumentDecodingException(documentKey, field, cause)
            }

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            while (nextIndex < descriptor.elementsCount) {
                val index = nextIndex++
                val present = bytes(descriptor, index) != null
                if (present || !descriptor.isElementOptional(index)) {
                    return index
                }
            }
            return CompositeDecoder.DECODE_DONE
        }

        override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean =
            read(descriptor, index, Boolean.serializer())

        override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte =
            read(descriptor, index, Byte.serializer())

        override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char =
            read(descriptor, index, Char.serializer())

        override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short =
            read(descriptor, index, Short.serializer())

        override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int =
            read(descriptor, index, Int.serializer())

        override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long =
            read(descriptor, index, Long.serializer())

        override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float =
            read(descriptor, index, Float.serializer())

        override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
            read(descriptor, index, Double.serializer())

        override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String =
            read(descriptor, index, String.serializer())

        override fun <T> decodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: DeserializationStrategy<T>,
            previousValue: T?,
        ): T = read(descriptor, index, deserializer)

        override fun <T : Any> decodeNullableSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: DeserializationStrategy<T?>,
            previousValue: T?,
        ): T? {
            val raw = bytes(descriptor, index)
            val isCborNull = raw != null && raw.size == 1 && raw[0] == CBOR_NULL
            return if (raw == null || isCborNull) null else decode(descriptor.getElementName(index), deserializer, raw)
        }

        override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
            throw UnsupportedOperationException(
                "Inline value class field '$documentKey${descriptor.getElementName(index)}' " +
                    "is not supported by Documents v1",
            )
        }

        override fun endStructure(descriptor: SerialDescriptor) {}
    }
}
