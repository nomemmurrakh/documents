package io.github.nomemmurrakh.documents

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Configuration for a [Documents] store, populated in the [Documents.create] block.
 */
public class DocumentsConfig internal constructor() {

    /** The JSON format used to encode and decode field values. */
    public var json: Json = DefaultJson

    /** Whether the backing storage is shared across processes. Defaults to `false`. */
    public var multiProcess: Boolean = false

    /**
     * The dispatcher on which [Document.flow] and [Document.stateFlow] collection runs. The work
     * is CPU-bound serialization, so this defaults to [Dispatchers.Default].
     */
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
}

/**
 * A store of typed documents backed by a single named storage area.
 *
 * Obtain a store with [create] (or [inMemory] for tests), then open a [Document] per key. All
 * documents from one store share a change bus, so a write to one document does not notify
 * observers of another.
 */
public interface Documents {

    /**
     * Opens the [Document] at [key], using [serializer] for its value type.
     *
     * @throws IllegalArgumentException when [key] contains the reserved key separator.
     */
    public fun <T> document(key: String, serializer: KSerializer<T>): Document<T>

    public companion object {

        /**
         * Creates a persistent store named [name], configured through [block].
         */
        public fun create(name: String, block: DocumentsConfig.() -> Unit = {}): Documents {
            ensureInitialized()
            val config = DocumentsConfig().apply(block)
            return DocumentsImpl(platformStorage(name, config.multiProcess), config.json, config.dispatcher)
        }

        /**
         * Creates a non-persistent, in-memory store. Intended for tests.
         */
        public fun inMemory(): Documents =
            DocumentsImpl(InMemoryStorage(), DefaultJson, Dispatchers.Default)
    }
}

/**
 * Opens the [Document] at [key], resolving the value type's serializer at the call site.
 *
 * @throws IllegalArgumentException when [key] contains the reserved key separator.
 */
public inline fun <reified T> Documents.document(key: String): Document<T> =
    document(key, serializer())

internal val DefaultJson: Json = Json { ignoreUnknownKeys = true }

internal expect fun ensureInitialized()

internal expect fun platformStorage(name: String, multiProcess: Boolean): Storage

internal class DocumentsImpl(
    private val storage: Storage,
    private val json: Json,
    private val dispatcher: CoroutineDispatcher,
) : Documents {

    private val changes = ChangeBus()

    override fun <T> document(key: String, serializer: KSerializer<T>): Document<T> {
        Keys.prefix(key)
        return DocumentImpl(key, serializer, storage, json, changes, dispatcher)
    }
}
