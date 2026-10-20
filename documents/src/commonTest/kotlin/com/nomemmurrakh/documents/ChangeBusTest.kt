package com.nomemmurrakh.documents

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class ChangeBusTest {

    @Serializable
    private data class User(val id: String, val age: Int, val active: Boolean)

    private fun document(key: String, store: StoreRuntime): Document<User> =
        DocumentImpl(key, User.serializer(), store, DefaultCbor)

    @Test
    fun setEmitsTheDocumentKeyAfterCommit() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val bus = store.changes
        val doc = document("user", store)

        val emitted = async { bus.keys.first() }
        yield()
        doc.set(User("1", 30, true))

        assertEquals("user", emitted.await())
    }

    @Test
    fun deleteEmitsTheDocumentKey() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val bus = store.changes
        val doc = document("user", store)
        doc.set(User("1", 30, true))

        val emitted = async { bus.keys.first() }
        yield()
        doc.delete()

        assertEquals("user", emitted.await())
    }

    @Test
    fun writesToDifferentKeysEmitOnlyTheirOwnKey() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val bus = store.changes
        val a = document("a", store)
        val b = document("b", store)

        val collected = async { bus.keys.take(2).toList() }
        yield()
        a.set(User("a", 1, true))
        b.set(User("b", 2, false))

        assertEquals(listOf("a", "b"), collected.await())
    }

    @Test
    fun emissionHappensAfterTheWriteIsCommitted() = runTest {
        val storage = InMemoryStorage()
        val store = StoreRuntime(storage)
        val bus = store.changes
        val doc = document("user", store)

        val seen = async {
            bus.keys.first()
            decodeDocument("user", User.serializer(), storage, DefaultCbor)
        }
        yield()
        doc.set(User("1", 7, true))

        assertEquals(User("1", 7, true), seen.await())
    }
}
