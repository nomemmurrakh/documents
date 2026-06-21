package io.github.nomemmurrakh.documents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class DocumentEncoderTest {

    @Serializable
    private enum class Theme { LIGHT, DARK, SYSTEM }

    @Serializable
    private data class Address(val city: String, val zip: Int)

    @Serializable
    private data class User(
        val id: String,
        val age: Int,
        val active: Boolean,
        val theme: Theme,
        val nickname: String?,
        val address: Address,
    )

    private val cbor = DefaultCbor

    private fun <T> decodeField(storage: Storage, doc: String, field: String, serializer: KSerializer<T>): T {
        val bytes = storage.getBytes(Keys.field(doc, field))!!
        return cbor.decodeFromByteArray(serializer, bytes)
    }

    @Test
    fun eachFieldIsStoredUnderItsOwnKey() {
        val storage: Storage = InMemoryStorage()
        val user = User("1", 30, true, Theme.DARK, "km", Address("Lahore", 54000))

        encodeDocument("user", user, User.serializer(), storage, cbor)

        assertEquals(
            listOf("user::active", "user::address", "user::age", "user::id", "user::nickname", "user::theme"),
            storage.keys("user::").sorted(),
        )
    }

    @Test
    fun multiFieldDataClassRoundTripsPerField() {
        val storage: Storage = InMemoryStorage()
        val user = User("42", 30, true, Theme.DARK, "km", Address("Lahore", 54000))

        encodeDocument("user", user, User.serializer(), storage, cbor)

        assertEquals("42", decodeField(storage, "user", "id", String.serializer()))
        assertEquals(30, decodeField(storage, "user", "age", Int.serializer()))
        assertEquals(true, decodeField(storage, "user", "active", Boolean.serializer()))
        assertEquals(Theme.DARK, decodeField(storage, "user", "theme", Theme.serializer()))
        assertEquals("km", decodeField(storage, "user", "nickname", String.serializer()))
        assertEquals(Address("Lahore", 54000), decodeField(storage, "user", "address", Address.serializer()))
    }

    @Test
    fun nullableNullFieldIsStoredAsCborNull() {
        val storage: Storage = InMemoryStorage()
        val user = User("1", 0, false, Theme.SYSTEM, null, Address("X", 1))

        encodeDocument("user", user, User.serializer(), storage, cbor)

        assertTrue(storage.contains("user::nickname"))
        assertNull(cbor.decodeFromByteArray(String.serializer().nullable, storage.getBytes("user::nickname")!!))
    }

    @Test
    fun nestedSerializableIsStoredAsSingleSubBlob() {
        val storage: Storage = InMemoryStorage()
        val user = User("1", 0, false, Theme.SYSTEM, null, Address("Lahore", 54000))

        encodeDocument("user", user, User.serializer(), storage, cbor)

        assertEquals(
            Address("Lahore", 54000),
            cbor.decodeFromByteArray(Address.serializer(), storage.getBytes("user::address")!!),
        )
        assertNull(storage.getBytes("user::address::city"))
    }

    @Test
    fun keysAreScopedToDocumentKey() {
        val storage: Storage = InMemoryStorage()

        encodeDocument("user", User("1", 1, true, Theme.LIGHT, null, Address("A", 1)), User.serializer(), storage, cbor)
        encodeDocument("post", User("2", 2, false, Theme.DARK, null, Address("B", 2)), User.serializer(), storage, cbor)

        assertEquals(6, storage.keys("user::").size)
        assertEquals(6, storage.keys("post::").size)
    }
}
