package com.nomemmurrakh.documents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

private class TagFieldDecorator(private val tag: Byte) : FieldDecorator {
    override fun wrap(fieldName: String, bytes: ByteArray): ByteArray = bytes + tag

    override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray {
        check(bytes.isNotEmpty() && bytes.last() == tag)
        return bytes.copyOfRange(0, bytes.size - 1)
    }
}

private class ThrowingUnwrapDecorator : FieldDecorator {
    override fun wrap(fieldName: String, bytes: ByteArray): ByteArray = bytes

    override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray =
        throw IllegalArgumentException("Simulated corrupted ciphertext for '$fieldName'")
}

private class CountingDecorator : FieldDecorator {
    var wrapCalls: Int = 0
        private set
    var unwrapCalls: Int = 0
        private set

    override fun wrap(fieldName: String, bytes: ByteArray): ByteArray {
        wrapCalls++
        return bytes
    }

    override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray {
        unwrapCalls++
        return bytes
    }
}

@OptIn(ExperimentalSerializationApi::class)
class FieldDecoratorIntegrationTest {

    @Serializable
    private data class Prefs(val theme: String = "system", val fontScale: Float = 1.0f)

    @Test
    fun setAndGetRoundTripThroughDecorator() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl(
            "prefs",
            Prefs.serializer(),
            storage,
            DefaultCbor,
            decorators = listOf(TagFieldDecorator(tag = 7)),
        )

        doc.set(Prefs(theme = "dark", fontScale = 1.5f))

        assertEquals(Prefs(theme = "dark", fontScale = 1.5f), doc.get())
    }

    @Test
    fun rawStorageBytesAreTaggedByTheDecorator() {
        val storage = InMemoryStorage()
        val decorated = DocumentImpl(
            "prefs",
            Prefs.serializer(),
            storage,
            DefaultCbor,
            decorators = listOf(TagFieldDecorator(tag = 7)),
        )
        val plain = DocumentImpl("plain", Prefs.serializer(), InMemoryStorage(), DefaultCbor)

        decorated.set(Prefs())
        plain.set(Prefs())

        val decoratedBytes = requireNotNull(storage.getBytes(Keys.field("prefs", "theme")))
        assertNotEquals(0, decoratedBytes.size)
        assertEquals(7, decoratedBytes.last())
    }

    @Test
    fun updateBuilderRoundTripsThroughDecorator() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl(
            "prefs",
            Prefs.serializer(),
            storage,
            DefaultCbor,
            decorators = listOf(TagFieldDecorator(tag = 3)),
        )
        doc.set(Prefs(theme = "light", fontScale = 1.0f))

        doc.update { current -> current.copy(fontScale = 2.0f) }

        assertEquals(Prefs(theme = "light", fontScale = 2.0f), doc.get())
    }

    @Test
    fun singleFieldUpdateAndFieldDelegateRoundTripThroughDecorator() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl(
            "prefs",
            Prefs.serializer(),
            storage,
            DefaultCbor,
            decorators = listOf(TagFieldDecorator(tag = 5)),
        )
        doc.set(Prefs(theme = "system", fontScale = 1.0f))

        doc.update(Prefs::fontScale, 3.0f)

        assertEquals(3.0f, doc.get()?.fontScale)

        var delegated: Float by doc.field(Prefs::fontScale, default = 1.0f)
        assertEquals(3.0f, delegated)
        delegated = 4.0f
        assertEquals(4.0f, doc.get()?.fontScale)
    }

    @Test
    fun collectionOnlyDecoratorAppliesToItsDocuments() {
        val collection = collection { decorators = listOf(TagFieldDecorator(tag = 1)) }
        val doc = collection.document<Prefs>("prefs")

        doc.set(Prefs(theme = "dark"))

        assertEquals(Prefs(theme = "dark"), doc.get())
    }

    @Test
    fun documentOnlyDecoratorAppliesWithoutCollectionDecorators() {
        val collection = collection()
        val doc = collection.document<Prefs>("prefs") { decorators = listOf(TagFieldDecorator(tag = 2)) }

        doc.set(Prefs(theme = "dark"))

        assertEquals(Prefs(theme = "dark"), doc.get())
    }

    @Test
    fun collectionAndDocumentDecoratorsBothApplyWithDocumentAppendedLast() {
        val collection = collection { decorators = listOf(TagFieldDecorator(tag = 1)) }
        val doc = collection.document<Prefs>("prefs") { decorators = listOf(TagFieldDecorator(tag = 2)) }

        doc.set(Prefs(theme = "dark"))

        assertEquals(Prefs(theme = "dark"), doc.get())
    }

    @Test
    fun collectionDecoratorMergeIsNotRecomputedPerReadOrWrite() {
        val counting = CountingDecorator()
        val collection = collection { decorators = listOf(counting) }
        val doc = collection.document<Prefs>("prefs")

        doc.set(Prefs(theme = "a", fontScale = 1.0f))
        doc.set(Prefs(theme = "b", fontScale = 1.0f))
        doc.get()

        assertEquals(4, counting.wrapCalls)
        assertEquals(4, counting.unwrapCalls)
    }

    @Test
    fun throwingUnwrapSurfacesAsDocumentDecodingExceptionViaGet() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl(
            "prefs",
            Prefs.serializer(),
            storage,
            DefaultCbor,
            decorators = listOf(ThrowingUnwrapDecorator()),
        )
        doc.set(Prefs(theme = "dark"))

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("prefs", failure.documentKey)
        assertEquals(IllegalArgumentException::class, failure.cause!!::class)
    }

    @Test
    fun throwingUnwrapSurfacesAsDocumentDecodingExceptionViaFieldDelegate() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl(
            "prefs",
            Prefs.serializer(),
            storage,
            DefaultCbor,
            decorators = listOf(ThrowingUnwrapDecorator()),
        )
        doc.set(Prefs(theme = "dark"))

        val delegated: String by doc.field(Prefs::theme, default = "system")
        val failure = assertFailsWith<DocumentDecodingException> { delegated }

        assertEquals("prefs", failure.documentKey)
        assertEquals("theme", failure.field)
        assertEquals(IllegalArgumentException::class, failure.cause!!::class)
    }

    private fun collection(config: CollectionConfig.() -> Unit = {}): Collection {
        val resolved = CollectionConfig().apply(config)
        return CollectionImpl(InMemoryStorage(), DefaultCbor, resolved.dispatcher, resolved.decorators)
    }
}
