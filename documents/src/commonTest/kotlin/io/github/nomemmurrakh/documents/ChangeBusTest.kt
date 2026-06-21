package io.github.nomemmurrakh.documents

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

    private fun document(key: String, storage: Storage, bus: ChangeBus): Document<User> =
        DocumentImpl(key, User.serializer(), storage, DefaultCbor, bus)

    @Test
    fun setEmitsTheDocumentKeyAfterCommit() = runTest {
        val bus = ChangeBus()
        val doc = document("user", InMemoryStorage(), bus)

        val emitted = async { bus.keys.first() }
        yield()
        doc.set(User("1", 30, true))

        assertEquals("user", emitted.await())
    }

    @Test
    fun deleteEmitsTheDocumentKey() = runTest {
        val bus = ChangeBus()
        val doc = document("user", InMemoryStorage(), bus)
        doc.set(User("1", 30, true))

        val emitted = async { bus.keys.first() }
        yield()
        doc.delete()

        assertEquals("user", emitted.await())
    }

    @Test
    fun writesToDifferentKeysEmitOnlyTheirOwnKey() = runTest {
        val bus = ChangeBus()
        val storage = InMemoryStorage()
        val a = document("a", storage, bus)
        val b = document("b", storage, bus)

        val collected = async { bus.keys.take(2).toList() }
        yield()
        a.set(User("a", 1, true))
        b.set(User("b", 2, false))

        assertEquals(listOf("a", "b"), collected.await())
    }

    @Test
    fun emissionHappensAfterTheWriteIsCommitted() = runTest {
        val bus = ChangeBus()
        val storage = InMemoryStorage()
        val doc = document("user", storage, bus)

        val seen = async {
            bus.keys.first()
            decodeDocument("user", User.serializer(), storage, DefaultCbor)
        }
        yield()
        doc.set(User("1", 7, true))

        assertEquals(User("1", 7, true), seen.await())
    }
}
