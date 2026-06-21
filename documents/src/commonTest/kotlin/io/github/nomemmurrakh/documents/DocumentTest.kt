package io.github.nomemmurrakh.documents

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun store(): Documents = Documents.inMemory()

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
        val doc = DocumentImpl("user", User.serializer(), storage, DefaultJson)
        doc.set(User("1", 1, true))
        storage.putBytes(Keys.field("user", "legacyField"), "\"stale\"".encodeToByteArray())

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

        doc.set(MergeStrategy.UPDATE) { copy(age = 31) }

        assertEquals(
            User("1", 31, true, Theme.DARK, "km", Address("Lahore", 54000)),
            doc.get(),
        )
    }

    @Test
    fun updateOnMissingDocumentStartsFromDefaults() {
        val doc = store().document<Prefs>("prefs")

        doc.set(MergeStrategy.UPDATE) { copy(fontScale = 125) }

        assertEquals(Prefs(theme = Theme.SYSTEM, fontScale = 125, nickname = null), doc.get())
    }

    @Test
    fun updateOnMissingPersistsOnlyFromDefaultBaseline() {
        val doc = store().document<Prefs>("prefs")

        doc.set(MergeStrategy.UPDATE) { copy(nickname = "km") }

        assertEquals(Prefs(theme = Theme.SYSTEM, fontScale = 100, nickname = "km"), doc.get())
    }

    @Test
    fun replaceBuilderStartsFromDefaultsIgnoringPersisted() {
        val doc = store().document<Prefs>("prefs")
        doc.set(Prefs(theme = Theme.DARK, fontScale = 200, nickname = "old"))

        doc.set(MergeStrategy.REPLACE) { copy(fontScale = 110) }

        assertEquals(Prefs(theme = Theme.SYSTEM, fontScale = 110, nickname = null), doc.get())
    }

    @Test
    fun updateLeavesNoStaleKeyForOverwrittenField() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("prefs", Prefs.serializer(), storage, DefaultJson)
        doc.set(Prefs(theme = Theme.DARK, fontScale = 200, nickname = "old"))

        doc.set(MergeStrategy.UPDATE) { copy(nickname = null) }

        assertEquals(Prefs(theme = Theme.DARK, fontScale = 200, nickname = null), doc.get())
    }

    @Test
    fun corruptFieldBytesThrowDocumentDecodingExceptionNamingKeyAndField() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("user", User.serializer(), storage, DefaultJson)
        doc.set(User("1", 30, true))
        storage.putBytes(Keys.field("user", "age"), "not-an-int".encodeToByteArray())

        val failure = assertFailsWith<DocumentDecodingException> { doc.get() }

        assertEquals("user", failure.documentKey)
        assertEquals("age", failure.field)
        assertNotNull(failure.cause)
    }

    @Test
    fun missingRequiredFieldOnPresentDocumentThrowsDocumentDecodingException() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("user", User.serializer(), storage, DefaultJson)
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
        val doc = DocumentImpl("prefs", Prefs.serializer(), storage, DefaultJson)
        doc.set(Prefs(theme = Theme.DARK, fontScale = 100, nickname = "x"))

        doc.set(MergeStrategy.UPDATE) { copy(fontScale = 150) }

        assertEquals(Prefs(theme = Theme.DARK, fontScale = 150, nickname = "x"), doc.get())
    }

    @Test
    fun multiFieldUpdateCommitsAllFieldsTogether() {
        val storage = InMemoryStorage()
        val doc = DocumentImpl("prefs", Prefs.serializer(), storage, DefaultJson)
        doc.set(Prefs(theme = Theme.LIGHT, fontScale = 100, nickname = "old"))

        doc.set(MergeStrategy.UPDATE) { copy(theme = Theme.DARK, fontScale = 200, nickname = "new") }

        assertEquals(Prefs(theme = Theme.DARK, fontScale = 200, nickname = "new"), doc.get())
    }
}
