package com.nomemmurrakh.documents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalSerializationApi::class)
class DecodingMessagesTest {

    @Serializable
    private data class Strict(val title: String, val count: Int)

    @Serializable
    private data class Prefs(val theme: String = "system", val fontScale: Float = 1.0f)

    private class ThrowingUnwrapDecorator(private val failure: () -> Throwable) : FieldDecorator {
        override fun wrap(fieldName: String, bytes: ByteArray): ByteArray = bytes
        override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray = throw failure()
    }

    private fun <T> document(
        key: String,
        serializer: KSerializer<T>,
        storage: Storage,
        decorators: List<FieldDecorator> = emptyList(),
    ): Document<T> = DocumentImpl(key, serializer, StoreRuntime(storage), DefaultCbor, decorators = decorators)

    private fun messageOf(failure: DocumentDecodingException): String = requireNotNull(failure.message)

    @Test
    fun updateOnANeverWrittenDocumentExplainsTheMissingDefault() {
        val doc = document("note", Strict.serializer(), InMemoryStorage())

        val failure = assertFailsWith<DocumentDecodingException> { doc.update { it } }

        assertEquals("note", failure.documentKey)
        assertEquals("title", failure.field)
        assertNull(failure.cause)
        val message = messageOf(failure)
        assertContains(message, "Cannot update document 'note'")
        assertContains(message, "field 'title' has no default value")
        assertContains(message, "`val title: String = ...`")
        assertContains(message, "`val title: String?`")
        assertContains(message, "call `set()`")
    }

    @Test
    fun aStoredDocumentMissingARequiredFieldExplainsTheMissingValue() {
        val storage = InMemoryStorage()
        val doc = document("note", Strict.serializer(), storage)
        doc.set(Strict(title = "Pick up milk", count = 1))
        storage.remove(Keys.field("note", "count"))

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("note", failure.documentKey)
        assertEquals("count", failure.field)
        assertNull(failure.cause)
        val message = messageOf(failure)
        assertContains(message, "Document 'note' is missing a stored value for required field 'count'")
        assertContains(message, "different version of your data class")
        assertFalse(message.contains("Cannot update"))
    }

    @Test
    fun corruptBytesExplainThatTheDataCouldNotBeRead() {
        val storage = InMemoryStorage()
        val doc = document("note", Strict.serializer(), storage)
        doc.set(Strict(title = "Pick up milk", count = 1))
        storage.putBytes(Keys.field("note", "count"), byteArrayOf(0x1A))

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("note", failure.documentKey)
        assertEquals("count", failure.field)
        assertNotNull(failure.cause)
        val message = messageOf(failure)
        assertContains(message, "Field 'count' of document 'note' could not be read as kotlin.Int")
        assertContains(message, "corrupted")
        assertContains(message, "incompatible version of your data class")
    }

    @Test
    fun corruptBytesReadThroughAFieldDelegateExplainThatTheDataCouldNotBeRead() {
        val storage = InMemoryStorage()
        val doc = document("prefs", Prefs.serializer(), storage)
        storage.putBytes(Keys.field("prefs", "fontScale"), byteArrayOf(0x1A))
        val fontScale by doc.field(Prefs::fontScale, default = 1.0f)

        val failure = assertFailsWith<DocumentDecodingException> { fontScale }

        assertEquals("fontScale", failure.field)
        assertContains(messageOf(failure), "Field 'fontScale' of document 'prefs' could not be read as kotlin.Float")
    }

    @Test
    fun aFailingDecoratorExplainsThatTheDecoratorFailed() {
        val storage = InMemoryStorage()
        val doc = document(
            "prefs",
            Prefs.serializer(),
            storage,
            listOf(ThrowingUnwrapDecorator { IllegalArgumentException("bad key") }),
        )
        doc.set(Prefs(theme = "dark"))

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("prefs", failure.documentKey)
        assertEquals("theme", failure.field)
        assertEquals(IllegalArgumentException::class, failure.cause!!::class)
        val message = messageOf(failure)
        assertContains(message, "A field decorator failed to unwrap field 'theme' of document 'prefs'")
        assertContains(message, "IllegalArgumentException")
        assertContains(message, "decorators, their order, and any keys match")
        assertFalse(message.contains("could not be read"))
    }

    @Test
    fun aFailingDecoratorReadThroughAFieldDelegateExplainsThatTheDecoratorFailed() {
        val storage = InMemoryStorage()
        val doc = document(
            "prefs",
            Prefs.serializer(),
            storage,
            listOf(ThrowingUnwrapDecorator { IllegalStateException("wrong key") }),
        )
        doc.set(Prefs(theme = "dark"))
        val theme by doc.field(Prefs::theme, default = "system")

        val failure = assertFailsWith<DocumentDecodingException> { theme }

        assertEquals("theme", failure.field)
        val message = messageOf(failure)
        assertContains(message, "A field decorator failed to unwrap field 'theme' of document 'prefs'")
        assertContains(message, "IllegalStateException")
        assertFalse(message.contains("could not be read"))
    }

    @Test
    fun theMessageNeverIncludesTheCausesOwnText() {
        val storage = InMemoryStorage()
        val doc = document(
            "session",
            Prefs.serializer(),
            storage,
            listOf(ThrowingUnwrapDecorator { IllegalArgumentException("token=abc123") }),
        )
        doc.set(Prefs(theme = "dark"))

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("token=abc123", failure.cause?.message)
        assertFalse(messageOf(failure).contains("abc123"))
    }

    @Test
    fun aFailureWithNoSingleFieldKeepsTheGeneralMessage() {
        val failure = DocumentDecodingException("note", field = null, cause = null)

        assertEquals("Failed to decode document 'note'", failure.message)
    }
}
