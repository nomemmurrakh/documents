package io.github.nomemmurrakh.documents

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentStateFlowTest {

    @Serializable
    private data class User(val id: String, val age: Int, val active: Boolean)

    private fun document(
        key: String,
        storage: Storage,
        bus: ChangeBus,
        dispatcher: CoroutineDispatcher,
    ): Document<User> =
        DocumentImpl(key, User.serializer(), storage, DefaultJson, bus, dispatcher)

    @Test
    fun stateFlowHoldsTheCurrentValueAsInitialState() = runTest {
        val storage = InMemoryStorage()
        val doc = document("user", storage, ChangeBus(), UnconfinedTestDispatcher(testScheduler))
        doc.set(User("1", 30, true))

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = doc.stateFlow(scope)

        assertEquals(User("1", 30, true), state.value)
        scope.cancel()
    }

    @Test
    fun stateFlowHoldsNullWhenTheDocumentIsAbsent() = runTest {
        val doc = document("user", InMemoryStorage(), ChangeBus(), UnconfinedTestDispatcher(testScheduler))

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        assertNull(doc.stateFlow(scope).value)
        scope.cancel()
    }

    @Test
    fun stateFlowUpdatesAfterACommittedWrite() = runTest {
        val storage = InMemoryStorage()
        val doc = document("user", storage, ChangeBus(), UnconfinedTestDispatcher(testScheduler))

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = doc.stateFlow(scope)
        val seen = mutableListOf<User?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { state.toList(seen) }
        doc.set(User("1", 7, true))
        collector.cancel()
        scope.cancel()

        assertEquals(listOf(null, User("1", 7, true)), seen)
    }

    @Test
    fun stateFlowUpdatesToNullOnDelete() = runTest {
        val storage = InMemoryStorage()
        val doc = document("user", storage, ChangeBus(), UnconfinedTestDispatcher(testScheduler))
        doc.set(User("1", 7, true))

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = doc.stateFlow(scope)
        val seen = mutableListOf<User?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { state.toList(seen) }
        doc.delete()
        collector.cancel()
        scope.cancel()

        assertEquals(listOf(User("1", 7, true), null), seen)
    }
}
