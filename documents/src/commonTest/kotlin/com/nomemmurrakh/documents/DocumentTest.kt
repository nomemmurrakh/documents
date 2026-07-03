package com.nomemmurrakh.documents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class DocumentTest {

    @Serializable
    private enum class Theme { LIGHT, DARK, SYSTEM }

    @Serializable
    private data class Address(val city: String, val zip: Int)

    @Serializable
    private data class User(
        val id: String,
        val age: Int,
        val active: Boolean,
        val theme: Theme = Theme.SYSTEM,
        val nickname: String? = null,
        val address: Address = Address("Unknown", 0),
    )

    @Serializable
    private data class Prefs(
        val theme: Theme = Theme.SYSTEM,
        val fontScale: Int = 100,
        val nickname: String? = null,
    )

    private fun store(): Collection = Documents.inMemory()

    @Test
    fun setThenGetRoundTripsUnchanged() {
        val doc = store().document<User>("user")
        val user = User("1", 30, true, Theme.DARK, "km", Address("Lahore", 54000))

        doc.set(user)

        assertEquals(user, doc.get())
    }

    @Test
    fun getOnAbsentDocumentReturnsNull() {
        assertNull(store().document<User>("user").get())
    }

    @Test
    fun existsIsFalseBeforeSetAndTrueAfter() {
        val doc = store().document<User>("user")

        assertFalse(doc.exists())
        doc.set(User("1", 1, true))
        assertTrue(doc.exists())
    }

    @Test
    fun deleteRemovesDocumentAndGetReturnsNull() {
        val doc = store().document<User>("user")
        doc.set(User("1", 1, true, Theme.DARK, "n", Address("A", 1)))

        doc.delete()

        assertFalse(doc.exists())
        assertNull(doc.get())
    }

    @Test
    fun replaceOverwritesAllFieldsLeavingNoStaleValue() {
        val doc = store().document<User>("user")
        doc.set(User("1", 30, true, Theme.DARK, "stale", Address("Lahore", 54000)))

        val replacement = User("2", 5, false, Theme.LIGHT, null, Address("Karachi", 75000))
        doc.set(replacement)

        assertEquals(replacement, doc.get())
    }

    @Test
    fun setReplaceDeletesStaleKeysNotInNewValue() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("user", User.serializer(), storage, DefaultCbor)
        doc.set(User("1", 1, true))
        storage.putBytes(
            Keys.field("user", "legacyField"),
            DefaultCbor.encodeToByteArray(String.serializer(), "stale"),
        )

        doc.set(User("2", 2, false))

        assertFalse(storage.contains(Keys.field("user", "legacyField")))
    }

    @Test
    fun documentsAreIsolatedByKey() {
        val store = store()
        val a = store.document<User>("a")
        val b = store.document<User>("b")

        a.set(User("a", 1, true))

        assertTrue(a.exists())
        assertFalse(b.exists())
        assertNull(b.get())
    }

    @Test
    fun updateChangesOnlyTouchedFieldsKeepingTheRest() {
        val doc = store().document<User>("user")
        doc.set(User("1", 30, true, Theme.DARK, "km", Address("Lahore", 54000)))

        doc.update { current -> current.copy(age = 31) }

        assertEquals(
            User("1", 31, true, Theme.DARK, "km", Address("Lahore", 54000)),
            doc.get(),
        )
    }

    @Test
    fun updateOnMissingDocumentStartsFromDefaults() {
        val doc = store().document<Prefs>("prefs")

        doc.update { current -> current.copy(fontScale = 125) }

        assertEquals(Prefs(theme = Theme.SYSTEM, fontScale = 125, nickname = null), doc.get())
    }

    @Test
    fun updateOnMissingPersistsOnlyFromDefaultBaseline() {
        val doc = store().document<Prefs>("prefs")

        doc.update { current -> current.copy(nickname = "km") }

        assertEquals(Prefs(theme = Theme.SYSTEM, fontScale = 100, nickname = "km"), doc.get())
    }

    @Test
    fun updateLeavesNoStaleKeyForOverwrittenField() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("prefs", Prefs.serializer(), storage, DefaultCbor)
        doc.set(Prefs(theme = Theme.DARK, fontScale = 200, nickname = "old"))

        doc.update { current -> current.copy(nickname = null) }

        assertEquals(Prefs(theme = Theme.DARK, fontScale = 200, nickname = null), doc.get())
    }

    @Test
    fun corruptFieldBytesThrowDocumentDecodingExceptionNamingKeyAndField() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("user", User.serializer(), storage, DefaultCbor)
        doc.set(User("1", 30, true))
        storage.putBytes(Keys.field("user", "age"), byteArrayOf(0x1A))

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("user", failure.documentKey)
        assertEquals("age", failure.field)
        assertNotNull(failure.cause)
    }

    @Test
    fun missingRequiredFieldOnPresentDocumentThrowsDocumentDecodingException() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("user", User.serializer(), storage, DefaultCbor)
        doc.set(User("1", 30, true))
        storage.remove(Keys.field("user", "age"))

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("user", failure.documentKey)
        assertEquals("age", failure.field)
    }

    @Test
    fun documentRejectsReservedSeparatorInKey() {
        assertFailsWith<IllegalArgumentException> {
            store().document<User>("bad::key")
        }
    }

    @Test
    fun updateAcquiresTheWriteLockReentrantlyWithoutDeadlock() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("prefs", Prefs.serializer(), storage, DefaultCbor)
        doc.set(Prefs(theme = Theme.DARK, fontScale = 100, nickname = "x"))

        doc.update { current -> current.copy(fontScale = 150) }

        assertEquals(Prefs(theme = Theme.DARK, fontScale = 150, nickname = "x"), doc.get())
    }

    @Test
    fun multiFieldUpdateCommitsAllFieldsTogether() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("prefs", Prefs.serializer(), storage, DefaultCbor)
        doc.set(Prefs(theme = Theme.LIGHT, fontScale = 100, nickname = "old"))

        doc.update { current -> current.copy(theme = Theme.DARK, fontScale = 200, nickname = "new") }

        assertEquals(Prefs(theme = Theme.DARK, fontScale = 200, nickname = "new"), doc.get())
    }
}
