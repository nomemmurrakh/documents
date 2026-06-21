package io.github.nomemmurrakh.documents

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class FieldDelegateTest {

    @Serializable
    private data class Prefs(val theme: String = "system", val fontScale: Float = 1.0f)

    private fun document(
        key: String,
        storage: Storage,
        bus: ChangeBus = ChangeBus(),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): Document<Prefs> =
        DocumentImpl(key, Prefs.serializer(), storage, DefaultCbor, bus, dispatcher)

    @Test
    fun readingANeverSetFieldReturnsItsDefault() {
        val doc = document("prefs", InMemoryStorage())
        val theme by doc.field(Prefs::theme, default = "system")

        assertEquals("system", theme)
    }

    @Test
    fun writingAFieldUpdatesOnlyThatFieldsKey() {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage)
        var theme by doc.field(Prefs::theme, default = "system")

        theme = "dark"

        assertEquals("dark", theme)
        assertEquals(listOf("prefs::theme"), storage.keys(Keys.prefix("prefs")))
    }

    @Test
    fun writingAFieldDoesNotTouchOtherFieldKeys() {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage)
        var theme by doc.field(Prefs::theme, default = "system")
        var fontScale by doc.field(Prefs::fontScale, default = 1.0f)

        theme = "dark"
        fontScale = 1.5f

        assertEquals("dark", theme)
        assertEquals(1.5f, fontScale)
        assertEquals(
            setOf("prefs::theme", "prefs::fontScale"),
            storage.keys(Keys.prefix("prefs")).toSet(),
        )
    }

    @Test
    fun aDelegatedWriteTriggersTheDocumentsFlow() = runTest {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage, dispatcher = UnconfinedTestDispatcher(testScheduler))
        var theme by doc.field(Prefs::theme, default = "system")

        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { doc.flow().toList(seen) }
        theme = "dark"
        collector.cancel()

        assertEquals(listOf(null, Prefs(theme = "dark")), seen)
    }

    @Test
    fun fieldFlowEmitsTheDefaultWhenTheFieldIsNeverSet() = runTest {
        val doc = document("prefs", InMemoryStorage(), dispatcher = UnconfinedTestDispatcher(testScheduler))

        val seen = mutableListOf<String>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            doc.fieldFlow(Prefs::theme, default = "system").toList(seen)
        }
        collector.cancel()

        assertEquals(listOf("system"), seen)
    }

    @Test
    fun fieldFlowEmitsTheNewValueAfterThatFieldIsWritten() = runTest {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage, dispatcher = UnconfinedTestDispatcher(testScheduler))
        var theme by doc.field(Prefs::theme, default = "system")

        val seen = mutableListOf<String>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            doc.fieldFlow(Prefs::theme, default = "system").toList(seen)
        }
        theme = "dark"
        collector.cancel()

        assertEquals(listOf("system", "dark"), seen)
    }

    @Test
    fun fieldFlowDoesNotEmitWhenADifferentFieldChanges() = runTest {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage, dispatcher = UnconfinedTestDispatcher(testScheduler))
        var theme by doc.field(Prefs::theme, default = "system")
        var fontScale by doc.field(Prefs::fontScale, default = 1.0f)

        val seen = mutableListOf<String>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            doc.fieldFlow(Prefs::theme, default = "system").toList(seen)
        }
        fontScale = 1.5f
        theme = "dark"
        collector.cancel()

        assertEquals(listOf("system", "dark"), seen)
    }
}
