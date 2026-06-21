package io.github.nomemmurrakh.documents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalSerializationApi::class)
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

    private val cbor = DefaultCbor

    private fun store(): Storage = InMemoryStorage()

    private fun <T> seed(storage: Storage, field: String, serializer: KSerializer<T>, value: T) {
        storage.putBytes(Keys.field("user", field), cbor.encodeToByteArray(serializer, value))
    }

    private fun roundTrip(doc: String, value: User, storage: Storage): User {
        encodeDocument(doc, value, User.serializer(), storage, cbor)
        return decodeDocument(doc, User.serializer(), storage, cbor)
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
        seed(storage, "id", String.serializer(), "7")
        seed(storage, "age", Int.serializer(), 5)
        seed(storage, "active", Boolean.serializer(), true)

        val decoded = decodeDocument("user", User.serializer(), storage, cbor)

        assertEquals(Theme.SYSTEM, decoded.theme)
        assertEquals(Address("Unknown", 0), decoded.address)
    }

    @Test
    fun missingNullableFieldDecodesToDefaultNull() {
        val storage = store()
        seed(storage, "id", String.serializer(), "7")
        seed(storage, "age", Int.serializer(), 5)
        seed(storage, "active", Boolean.serializer(), true)

        assertNull(decodeDocument("user", User.serializer(), storage, cbor).nickname)
    }

    @Test
    fun explicitlyStoredNullNullableFieldDecodesToNull() {
        val storage = store()
        val user = User("1", 1, true, Theme.DARK, nickname = null, address = Address("A", 1))
        encodeDocument("user", user, User.serializer(), storage, cbor)

        val decoded = decodeDocument("user", User.serializer(), storage, cbor)

        assertNull(decoded.nickname)
        assertEquals(user, decoded)
    }

    @Test
    fun partialUpdateThenDecodeMergesPersistedAndDefaults() {
        val storage = store()
        encodeDocument("user", User("1", 20, true, Theme.DARK, "n", Address("C", 9)), User.serializer(), storage, cbor)

        seed(storage, "age", Int.serializer(), 21)

        val decoded = decodeDocument("user", User.serializer(), storage, cbor)

        assertEquals(21, decoded.age)
        assertEquals(Theme.DARK, decoded.theme)
        assertEquals("n", decoded.nickname)
    }

    @Test
    fun unknownStoredKeysAreIgnored() {
        val storage = store()
        val user = User("1", 1, true, Theme.LIGHT, null, Address("A", 1))
        encodeDocument("user", user, User.serializer(), storage, cbor)
        seed(storage, "legacyField", String.serializer(), "stale")

        assertEquals(user, decodeDocument("user", User.serializer(), storage, cbor))
    }

    @Test
    fun missingRequiredFieldFails() {
        val storage = store()
        seed(storage, "id", String.serializer(), "7")

        val failure = assertFailsWith<DocumentDecodingException> {
            decodeDocument("user", User.serializer(), storage, cbor)
        }

        assertEquals("user", failure.documentKey)
        assertEquals("age", failure.field)
    }
}
