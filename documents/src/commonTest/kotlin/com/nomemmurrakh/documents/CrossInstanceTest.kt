package com.nomemmurrakh.documents

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class CrossInstanceTest {

    @Serializable
    private data class Prefs(val theme: String = "system", val fontScale: Float = 1.0f)

    @Serializable
    private data class Counter(val count: Int = 0)

    private fun collection(
        store: StoreRuntime,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): Collection = CollectionImpl(store, DefaultCbor, dispatcher, emptyList())

    @Test
    fun flowOfOneInstanceEmitsAWriteMadeThroughAnotherInstance() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val reader = collection(store, dispatcher).document<Prefs>("prefs")
        val writer = collection(store, dispatcher).document<Prefs>("prefs")

        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { reader.flow().toList(seen) }
        writer.set(Prefs(theme = "dark"))
        collector.cancel()

        assertEquals(listOf(null, Prefs(theme = "dark")), seen)
    }

    @Test
    fun flowOfOneInstanceEmitsNullWhenAnotherInstanceDeletes() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val reader = collection(store, dispatcher).document<Prefs>("prefs")
        val writer = collection(store, dispatcher).document<Prefs>("prefs")
        writer.set(Prefs(theme = "dark"))

        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { reader.flow().toList(seen) }
        writer.delete()
        collector.cancel()

        assertEquals(listOf(Prefs(theme = "dark"), null), seen)
    }

    @Test
    fun stateFlowOfOneInstanceUpdatesAfterAWriteThroughAnotherInstance() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val reader = collection(store, dispatcher).document<Prefs>("prefs")
        val writer = collection(store, dispatcher).document<Prefs>("prefs")

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = reader.stateFlow(scope)
        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { state.toList(seen) }
        writer.set(Prefs(theme = "dark"))
        collector.cancel()
        scope.cancel()

        assertEquals(listOf(null, Prefs(theme = "dark")), seen)
    }

    @Test
    fun fieldFlowOfOneInstanceEmitsAFieldWrittenThroughAnotherInstance() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val reader = collection(store, dispatcher).document<Prefs>("prefs")
        val writer = collection(store, dispatcher).document<Prefs>("prefs")

        val seen = mutableListOf<String>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.fieldFlow(Prefs::theme, default = "system").toList(seen)
        }
        writer.update(Prefs::theme, "dark")
        collector.cancel()

        assertEquals(listOf("system", "dark"), seen)
    }

    @Test
    fun instancesOpenedThroughTheRuntimeShareOneStore() = runTest {
        val id = "cross-instance-test-through-runtime"
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val reader = collection(Runtime.store(id) { InMemoryStorage() }, dispatcher).document<Prefs>("prefs")
        val writer = collection(Runtime.store(id) { InMemoryStorage() }, dispatcher).document<Prefs>("prefs")

        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { reader.flow().toList(seen) }
        writer.set(Prefs(theme = "dark"))
        collector.cancel()

        assertEquals(listOf(null, Prefs(theme = "dark")), seen)
    }

    @Test
    fun concurrentUpdatesThroughSeparateInstancesLoseNoWrite() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val first = collection(store).document<Counter>("counter")
        val second = collection(store).document<Counter>("counter")
        val updatesPerInstance = 500

        withContext(Dispatchers.Default) {
            val jobs = List(updatesPerInstance) {
                listOf(
                    async { first.update { counter -> counter.copy(count = counter.count + 1) } },
                    async { second.update { counter -> counter.copy(count = counter.count + 1) } },
                )
            }.flatten()
            jobs.awaitAll()
        }

        assertEquals(updatesPerInstance * 2, first.get()?.count)
    }

    @Test
    fun sameKeyInDifferentCollectionsDoesNotCrossNotify() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val watched = collection(StoreRuntime(InMemoryStorage()), dispatcher).document<Prefs>("prefs")
        val other = collection(StoreRuntime(InMemoryStorage()), dispatcher).document<Prefs>("prefs")

        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { watched.flow().toList(seen) }
        other.set(Prefs(theme = "dark"))
        collector.cancel()

        assertEquals(listOf<Prefs?>(null), seen)
    }

    @Test
    fun aWriteToAnotherDocumentInTheSameStoreDoesNotNotifyACollector() = runTest {
        val store = StoreRuntime(InMemoryStorage())
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val watched = collection(store, dispatcher).document<Prefs>("prefs")
        val other = collection(store, dispatcher).document<Prefs>("other")

        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { watched.flow().toList(seen) }
        other.set(Prefs(theme = "dark"))
        collector.cancel()

        assertEquals(listOf<Prefs?>(null), seen)
    }
}
