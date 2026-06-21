package io.github.nomemmurrakh.documents

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KotlinxCodecTest {

    @Serializable
    private enum class Theme { LIGHT, DARK, SYSTEM }

    @Serializable
    private data class Address(val city: String, val zip: Int)

    private fun <T> codec(serializer: KSerializer<T>): KotlinxCodec<T> =
        KotlinxCodec(serializer)

    private fun <T> roundTrip(serializer: KSerializer<T>, value: T): T {
        val c = codec(serializer)
        return c.decode(c.encode(value), serializer)
    }

    @Test
    fun stringRoundTrips() {
        assertEquals("Khuram", roundTrip(String.serializer(), "Khuram"))
    }

    @Test
    fun intRoundTrips() {
        assertEquals(42, roundTrip(Int.serializer(), 42))
    }

    @Test
    fun booleanRoundTrips() {
        assertEquals(true, roundTrip(Boolean.serializer(), true))
    }

    @Test
    fun doubleRoundTrips() {
        assertEquals(3.14, roundTrip(Double.serializer(), 3.14))
    }

    @Test
    fun enumRoundTrips() {
        assertEquals(Theme.DARK, roundTrip(Theme.serializer(), Theme.DARK))
    }

    @Test
    fun nullableNonNullValueRoundTrips() {
        assertEquals("x", roundTrip(String.serializer().nullable, "x"))
    }

    @Test
    fun nullableNullValueRoundTrips() {
        assertNull(roundTrip(String.serializer().nullable, null))
    }

    @Test
    fun nestedSerializableSubBlobRoundTrips() {
        val address = Address(city = "Lahore", zip = 54000)
        assertEquals(address, roundTrip(Address.serializer(), address))
    }

    @Test
    fun jsonConfigurationIsRespected() {
        val pretty = KotlinxCodec(Address.serializer(), Json { prettyPrint = true })
        val compact = KotlinxCodec(Address.serializer(), Json { prettyPrint = false })
        val address = Address(city = "Lahore", zip = 54000)

        val prettyBytes = pretty.encode(address).decodeToString()
        val compactBytes = compact.encode(address).decodeToString()

        assertEquals(true, prettyBytes.contains('\n'))
        assertEquals(false, compactBytes.contains('\n'))
        assertEquals(address, pretty.decode(prettyBytes.encodeToByteArray(), Address.serializer()))
    }
}
