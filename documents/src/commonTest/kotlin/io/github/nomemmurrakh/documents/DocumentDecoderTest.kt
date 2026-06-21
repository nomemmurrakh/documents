package io.github.nomemmurrakh.documents

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DocumentDecoderTest {

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

    private val json = Json

    private fun store(): Storage = InMemoryStorage()

    private fun roundTrip(doc: String, value: User, storage: Storage): User {
        encodeDocument(doc, value, User.serializer(), storage, json)
        return decodeDocument(doc, User.serializer(), storage, json)
    }

    @Test
    fun fullMultiFieldRoundTrip() {
        val storage = store()
        val user = User("42", 30, true, Theme.DARK, "km", Address("Lahore", 54000))
        assertEquals(user, roundTrip("user", user, storage))
    }

    @Test
    fun missingOptionalFieldUsesDefault() {
        val storage = store()
        storage.putBytes(Keys.field("user", "id"), "\"7\"".encodeToByteArray())
        storage.putBytes(Keys.field("user", "age"), "5".encodeToByteArray())
        storage.putBytes(Keys.field("user", "active"), "true".encodeToByteArray())

        val decoded = decodeDocument("user", User.serializer(), storage, json)

        assertEquals(Theme.SYSTEM, decoded.theme)
        assertEquals(Address("Unknown", 0), decoded.address)
    }

    @Test
    fun missingNullableFieldDecodesToDefaultNull() {
        val storage = store()
        storage.putBytes(Keys.field("user", "id"), "\"7\"".encodeToByteArray())
        storage.putBytes(Keys.field("user", "age"), "5".encodeToByteArray())
        storage.putBytes(Keys.field("user", "active"), "true".encodeToByteArray())

        assertNull(decodeDocument("user", User.serializer(), storage, json).nickname)
    }

    @Test
    fun explicitlyStoredNullNullableFieldDecodesToNull() {
        val storage = store()
        val user = User("1", 1, true, Theme.DARK, nickname = null, address = Address("A", 1))
        encodeDocument("user", user, User.serializer(), storage, json)

        val decoded = decodeDocument("user", User.serializer(), storage, json)

        assertNull(decoded.nickname)
        assertEquals(user, decoded)
    }

    @Test
    fun partialUpdateThenDecodeMergesPersistedAndDefaults() {
        val storage = store()
        encodeDocument("user", User("1", 20, true, Theme.DARK, "n", Address("C", 9)), User.serializer(), storage, json)

        storage.putBytes(Keys.field("user", "age"), "21".encodeToByteArray())

        val decoded = decodeDocument("user", User.serializer(), storage, json)

        assertEquals(21, decoded.age)
        assertEquals(Theme.DARK, decoded.theme)
        assertEquals("n", decoded.nickname)
    }

    @Test
    fun unknownStoredKeysAreIgnored() {
        val storage = store()
        val user = User("1", 1, true, Theme.LIGHT, null, Address("A", 1))
        encodeDocument("user", user, User.serializer(), storage, json)
        storage.putBytes(Keys.field("user", "legacyField"), "\"stale\"".encodeToByteArray())

        assertEquals(user, decodeDocument("user", User.serializer(), storage, json))
    }

    @Test
    fun missingRequiredFieldFails() {
        val storage = store()
        storage.putBytes(Keys.field("user", "id"), "\"7\"".encodeToByteArray())

        val failure = assertFailsWith<DocumentDecodingException> {
            decodeDocument("user", User.serializer(), storage, json)
        }

        assertEquals("user", failure.documentKey)
        assertEquals("age", failure.field)
    }
}
