package com.nomemmurrakh.documents

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class DocumentFlowTest {

    @Serializable
    private data class User(val id: String, val age: Int, val active: Boolean)

    private fun document(
        key: String,
        storage: Storage,
        bus: ChangeBus,
        dispatcher: CoroutineDispatcher,
    ): Document<User> =
        DocumentImpl(key, User.serializer(), storage, DefaultCbor, bus, dispatcher)

    @Test
    fun flowEmitsTheCurrentValueOnCollection() = runTest {
        val bus = ChangeBus()
        val storage = InMemoryStorage()
        val doc = document("user", storage, bus, UnconfinedTestDispatcher(testScheduler))
        doc.set(User("1", 30, true))

        assertEquals(User("1", 30, true), doc.flow().first())
    }

    @Test
    fun flowEmitsNullWhenTheDocumentIsAbsent() = runTest {
        val bus = ChangeBus()
        val doc = document("user", InMemoryStorage(), bus, UnconfinedTestDispatcher(testScheduler))

        assertNull(doc.flow().first())
    }

    @Test
    fun flowEmitsTheNewValueAfterACommittedWrite() = runTest {
        val bus = ChangeBus()
        val storage = InMemoryStorage()
        val doc = document("user", storage, bus, UnconfinedTestDispatcher(testScheduler))

        val seen = mutableListOf<User?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { doc.flow().toList(seen) }
        doc.set(User("1", 7, true))
        collector.cancel()

        assertEquals(listOf(null, User("1", 7, true)), seen)
    }

    @Test
    fun flowEmitsNullOnDelete() = runTest {
        val bus = ChangeBus()
        val storage = InMemoryStorage()
        val doc = document("user", storage, bus, UnconfinedTestDispatcher(testScheduler))
        doc.set(User("1", 7, true))

        val seen = mutableListOf<User?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { doc.flow().toList(seen) }
        doc.delete()
        collector.cancel()

        assertEquals(listOf(User("1", 7, true), null), seen)
    }

    @Test
    fun flowDoesNotEmitForAWriteToADifferentDocument() = runTest {
        val bus = ChangeBus()
        val storage = InMemoryStorage()
        val watched = document("user", storage, bus, UnconfinedTestDispatcher(testScheduler))
        val other = document("other", storage, bus, UnconfinedTestDispatcher(testScheduler))

        val seen = mutableListOf<User?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { watched.flow().toList(seen) }
        other.set(User("x", 1, false))
        watched.set(User("1", 7, true))
        collector.cancel()

        assertEquals(listOf(null, User("1", 7, true)), seen)
    }
}
