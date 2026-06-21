package io.github.nomemmurrakh.documents

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer

/**
 * Configuration for a single document opened on the default store, populated in the
 * [Documents.document] block.
 */
public class DocumentConfig internal constructor() {

    /**
     * The dispatcher on which [Document.flow] and [Document.stateFlow] collection runs. The work
     * is CPU-bound serialization, so this defaults to [Dispatchers.Default].
     */
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
}

/**
 * Configuration for a [Collection], populated in the [Documents.collection] block.
 */
public class CollectionConfig internal constructor() {

    /** Whether the backing storage is shared across processes. Defaults to `false`. */
    public var multiProcess: Boolean = false

    /**
     * The dispatcher on which [Document.flow] and [Document.stateFlow] collection runs. The work
     * is CPU-bound serialization, so this defaults to [Dispatchers.Default].
     */
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
}

/**
 * A named group of documents backed by a single MMKV file.
 *
 * Open a collection only when a set of documents needs a distinct lifecycle or access pattern —
 * a wipe-on-logout cache, per-user scoping, multi-process sharing, or an encryption boundary.
 * For the common case, open documents directly on the default store with [Documents.document].
 *
 * All documents from one collection share a change bus, so a write to one document does not
 * notify observers of another.
 */
public interface Collection {

    /**
     * Opens the [Document] at [key], using [serializer] for its value type.
     *
     * @throws IllegalArgumentException when [key] contains the reserved key separator.
     */
    public fun <T> document(key: String, serializer: KSerializer<T>): Document<T>
}

/**
 * The entry point to the library. Open documents on the default store with [document], or open a
 * named [Collection] with [collection] when a separate MMKV file is warranted.
 */
@OptIn(ExperimentalSerializationApi::class)
public object Documents {

    /**
     * Opens the [Document] at [key] on the default store, using [serializer] for its value type
     * and configured through [config].
     *
     * @throws IllegalArgumentException when [key] contains the reserved key separator.
     */
    public fun <T> document(
        key: String,
        serializer: KSerializer<T>,
        config: DocumentConfig.() -> Unit = {},
    ): Document<T> {
        ensureInitialized()
        val resolved = DocumentConfig().apply(config)
        val storage = platformStorage(DEFAULT_STORE_ID, multiProcess = false)
        return CollectionImpl(storage, DefaultCbor, resolved.dispatcher).document(key, serializer)
    }

    /**
     * Opens the named [Collection], backed by its own MMKV file, configured through [config].
     */
    public fun collection(name: String, config: CollectionConfig.() -> Unit = {}): Collection {
        ensureInitialized()
        val resolved = CollectionConfig().apply(config)
        return CollectionImpl(platformStorage(name, resolved.multiProcess), DefaultCbor, resolved.dispatcher)
    }

    /**
     * Creates a non-persistent, in-memory [Collection]. Intended for tests.
     */
    public fun inMemory(): Collection =
        CollectionImpl(InMemoryStorage(), DefaultCbor, Dispatchers.Default)
}

/**
 * Opens the [Document] at [key] on the default store, resolving the value type's serializer at
 * the call site.
 *
 * @throws IllegalArgumentException when [key] contains the reserved key separator.
 */
public inline fun <reified T> Documents.document(
    key: String,
    noinline config: DocumentConfig.() -> Unit = {},
): Document<T> =
    document(key, serializer(), config)

/**
 * Opens the [Document] at [key] in this collection, resolving the value type's serializer at the
 * call site.
 *
 * @throws IllegalArgumentException when [key] contains the reserved key separator.
 */
public inline fun <reified T> Collection.document(key: String): Document<T> =
    document(key, serializer())

internal const val DEFAULT_STORE_ID: String = "documents.default"

@OptIn(ExperimentalSerializationApi::class)
internal val DefaultCbor: Cbor = Cbor { ignoreUnknownKeys = true }

internal expect fun ensureInitialized()

internal expect fun platformStorage(name: String, multiProcess: Boolean): Storage

@OptIn(ExperimentalSerializationApi::class)
internal class CollectionImpl(
    private val storage: Storage,
    private val cbor: Cbor,
    private val dispatcher: CoroutineDispatcher,
) : Collection {

    private val changes = ChangeBus()

    override fun <T> document(key: String, serializer: KSerializer<T>): Document<T> {
        Keys.prefix(key)
        return DocumentImpl(key, serializer, storage, cbor, changes, dispatcher)
    }
}
