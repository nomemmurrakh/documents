package io.github.nomemmurrakh.documents

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryStorageTest {

    private val storage: Storage = InMemoryStorage()

    @Test
    fun getReturnsNullForAbsentKey() {
        assertNull(storage.getBytes("missing"))
    }

    @Test
    fun putThenGetRoundTrips() {
        val value = byteArrayOf(1, 2, 3)
        storage.putBytes("user::name", value)
        assertContentEquals(value, storage.getBytes("user::name"))
    }

    @Test
    fun putOverwritesExistingValue() {
        storage.putBytes("k", byteArrayOf(1))
        storage.putBytes("k", byteArrayOf(2, 3))
        assertContentEquals(byteArrayOf(2, 3), storage.getBytes("k"))
    }

    @Test
    fun getReturnsDefensiveCopy() {
        val value = byteArrayOf(1, 2, 3)
        storage.putBytes("k", value)
        storage.getBytes("k")!![0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), storage.getBytes("k"))
    }

    @Test
    fun putStoresDefensiveCopy() {
        val value = byteArrayOf(1, 2, 3)
        storage.putBytes("k", value)
        value[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), storage.getBytes("k"))
    }

    @Test
    fun containsReflectsPresence() {
        assertFalse(storage.contains("k"))
        storage.putBytes("k", byteArrayOf(1))
        assertTrue(storage.contains("k"))
    }

    @Test
    fun removeDeletesKey() {
        storage.putBytes("k", byteArrayOf(1))
        storage.remove("k")
        assertFalse(storage.contains("k"))
        assertNull(storage.getBytes("k"))
    }

    @Test
    fun removeOfAbsentKeyIsNoOp() {
        storage.remove("missing")
        assertFalse(storage.contains("missing"))
    }

    @Test
    fun keysReturnsOnlyMatchingPrefix() {
        storage.putBytes("user::id", byteArrayOf(1))
        storage.putBytes("user::name", byteArrayOf(2))
        storage.putBytes("post::title", byteArrayOf(3))

        val userKeys = storage.keys("user::").sorted()

        assertEquals(listOf("user::id", "user::name"), userKeys)
    }

    @Test
    fun keysWithEmptyPrefixReturnsAll() {
        storage.putBytes("a", byteArrayOf(1))
        storage.putBytes("b", byteArrayOf(2))
        assertEquals(2, storage.keys("").size)
    }

    @Test
    fun keysReturnsEmptyWhenNoMatch() {
        storage.putBytes("user::id", byteArrayOf(1))
        assertTrue(storage.keys("post::").isEmpty())
    }
}
