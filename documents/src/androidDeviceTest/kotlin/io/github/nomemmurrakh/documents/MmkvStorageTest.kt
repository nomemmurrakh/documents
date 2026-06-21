package io.github.nomemmurrakh.documents

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.mmkv.MMKV
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MmkvStorageTest {

    private lateinit var mmapId: String

    @BeforeTest
    fun setUp() {
        MMKV.initialize(ApplicationProvider.getApplicationContext())
        mmapId = "documents-test-${System.nanoTime()}"
    }

    @AfterTest
    fun tearDown() {
        MMKV.mmkvWithID(mmapId).clearAll()
    }

    private fun storage(): MmkvStorage = MmkvStorage(MMKV.mmkvWithID(mmapId))

    @Test
    fun putThenGetRoundTrips() {
        val storage = storage()
        val value = byteArrayOf(1, 2, 3)
        storage.putBytes("user::name", value)
        assertContentEquals(value, storage.getBytes("user::name"))
    }

    @Test
    fun getReturnsNullForAbsentKey() {
        assertNull(storage().getBytes("missing"))
    }

    @Test
    fun removeDeletesKey() {
        val storage = storage()
        storage.putBytes("k", byteArrayOf(1))
        storage.remove("k")
        assertFalse(storage.contains("k"))
        assertNull(storage.getBytes("k"))
    }

    @Test
    fun keysReturnsOnlyMatchingPrefix() {
        val storage = storage()
        storage.putBytes("user::id", byteArrayOf(1))
        storage.putBytes("user::name", byteArrayOf(2))
        storage.putBytes("post::title", byteArrayOf(3))

        assertEquals(listOf("user::id", "user::name"), storage.keys("user::").sorted())
    }

    @Test
    fun persistsAcrossInstanceRecreation() {
        val value = byteArrayOf(7, 8, 9)
        MmkvStorage(MMKV.mmkvWithID(mmapId)).putBytes("k", value)

        val reopened = MmkvStorage(MMKV.mmkvWithID(mmapId))

        assertTrue(reopened.contains("k"))
        assertContentEquals(value, reopened.getBytes("k"))
    }

    @Test
    fun behaviorParityWithInMemoryStorage() {
        val mmkv: Storage = storage()
        val inMemory: Storage = InMemoryStorage()

        listOf(mmkv, inMemory).forEach { s ->
            assertNull(s.getBytes("user::id"))
            assertFalse(s.contains("user::id"))

            s.putBytes("user::id", byteArrayOf(1))
            s.putBytes("user::name", byteArrayOf(2, 3))
            s.putBytes("post::title", byteArrayOf(4))

            assertTrue(s.contains("user::id"))
            assertContentEquals(byteArrayOf(2, 3), s.getBytes("user::name"))
            assertEquals(listOf("user::id", "user::name"), s.keys("user::").sorted())

            s.putBytes("user::id", byteArrayOf(9))
            assertContentEquals(byteArrayOf(9), s.getBytes("user::id"))

            s.remove("user::id")
            assertFalse(s.contains("user::id"))
            assertNull(s.getBytes("user::id"))
        }
    }
}
