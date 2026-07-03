package com.nomemmurrakh.documents

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class DocumentUpdateFieldTest {

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
    fun updateFieldWritesOnlyThatFieldsKey() {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage)
        doc.set(Prefs(theme = "system", fontScale = 1.0f))

        doc.update(Prefs::fontScale, 1.5f)

        assertEquals(1.5f, doc.get()?.fontScale)
        assertEquals(
            setOf("prefs::theme", "prefs::fontScale"),
            storage.keys(Keys.prefix("prefs")).toSet(),
        )
    }

    @Test
    fun updateFieldDoesNotTouchOtherFieldKeys() {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage)
        doc.set(Prefs(theme = "dark", fontScale = 1.0f))
        val themeKeyBefore = storage.getBytes(Keys.field("prefs", "theme"))

        doc.update(Prefs::fontScale, 1.5f)

        assertEquals(themeKeyBefore?.toList(), storage.getBytes(Keys.field("prefs", "theme"))?.toList())
    }

    @Test
    fun updateFieldTriggersTheDocumentsFlow() = runTest {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage, dispatcher = UnconfinedTestDispatcher(testScheduler))
        doc.set(Prefs(theme = "system", fontScale = 1.0f))

        val seen = mutableListOf<Prefs?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { doc.flow().toList(seen) }
        doc.update(Prefs::fontScale, 1.5f)
        collector.cancel()

        assertEquals(1.5f, seen.last()?.fontScale)
    }

    @Test
    fun updateFieldSucceedsEvenWhenAnotherFieldIsCorrupt() {
        val storage = InMemoryStorage()
        val doc = document("prefs", storage)
        doc.set(Prefs(theme = "system", fontScale = 1.0f))
        storage.putBytes(Keys.field("prefs", "theme"), byteArrayOf(0x1A))

        doc.update(Prefs::fontScale, 1.5f)

        val fontScale = DefaultCbor.decodeFromByteArray(
            Float.serializer(),
            requireNotNull(storage.getBytes(Keys.field("prefs", "fontScale"))),
        )
        assertEquals(1.5f, fontScale)
    }
}
