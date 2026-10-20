package com.nomemmurrakh.documents

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class StoreRuntimeTest {

    @Serializable
    private data class Prefs(val theme: String = "system")

    @Test
    fun lockForReturnsTheSameLockForTheSameKey() {
        val store = StoreRuntime(InMemoryStorage())

        assertSame(store.lockFor("settings"), store.lockFor("settings"))
    }

    @Test
    fun lockForReturnsDifferentLocksForKeysOnDifferentStripes() {
        val store = StoreRuntime(InMemoryStorage())

        assertNotSame(store.lockFor("a"), store.lockFor("b"))
    }

    @Test
    fun lockForSharesALockBetweenKeysOnTheSameStripe() {
        val store = StoreRuntime(InMemoryStorage())
        val collidingKey = (('a'.code) + StoreRuntime.STRIPE_COUNT).toChar().toString()

        assertEquals(StoreRuntime.stripeIndex("a"), StoreRuntime.stripeIndex(collidingKey))
        assertSame(store.lockFor("a"), store.lockFor(collidingKey))
    }

    @Test
    fun stripeIndexStaysInRangeForAKeyWhoseHashCodeIsIntMinValue() {
        val key = "polygenelubricants"

        assertEquals(Int.MIN_VALUE, key.hashCode())
        assertTrue(StoreRuntime.stripeIndex(key) in 0 until StoreRuntime.STRIPE_COUNT)
    }

    @Test
    fun locksAreNotSharedAcrossStores() {
        val first = StoreRuntime(InMemoryStorage())
        val second = StoreRuntime(InMemoryStorage())

        assertNotSame(first.lockFor("settings"), second.lockFor("settings"))
    }

    @Test
    fun runtimeReturnsTheSameStoreForTheSameId() {
        val first = Runtime.store("store-runtime-test-same-id") { InMemoryStorage() }
        val second = Runtime.store("store-runtime-test-same-id") { InMemoryStorage() }

        assertSame(first, second)
        assertSame(first.changes, second.changes)
    }

    @Test
    fun runtimeOpensStorageOnlyOnceForAnId() {
        var opened = 0
        val openStorage = {
            opened++
            InMemoryStorage()
        }
        Runtime.store("store-runtime-test-open-once", openStorage)
        Runtime.store("store-runtime-test-open-once", openStorage)

        assertEquals(1, opened)
    }

    @Test
    fun runtimeKeepsDifferentIdsSeparate() {
        val first = Runtime.store("store-runtime-test-separate-a") { InMemoryStorage() }
        val second = Runtime.store("store-runtime-test-separate-b") { InMemoryStorage() }

        assertNotSame(first, second)
        assertNotSame(first.changes, second.changes)
    }

    @Test
    fun inMemoryCollectionsDoNotShareState() {
        val first = Documents.inMemory().document<Prefs>("prefs")
        val second = Documents.inMemory().document<Prefs>("prefs")

        first.set(Prefs(theme = "dark"))

        assertNull(second.get())
    }
}
