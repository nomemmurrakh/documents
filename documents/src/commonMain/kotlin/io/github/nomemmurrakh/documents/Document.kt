package io.github.nomemmurrakh.documents

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor

/**
 * A typed, document-oriented view over a single key in storage.
 *
 * Each document is stored as one key per field. Reads and writes are synchronous and
 * non-blocking; writes to the same document are serialized so a builder-style update is
 * atomic. Reactive observation is available through [flow] and [stateFlow].
 *
 * @param T the document's value type, which must be `@Serializable`.
 */
public interface Document<T> {

    /**
     * Returns the current value, or `null` when the document is absent.
     *
     * @throws DocumentDecodingException when a stored field cannot be decoded.
     */
    public fun get(): T?

    /**
     * Replaces the document with [value], removing any field keys not present in it.
     */
    public fun set(value: T)

    /**
     * Updates the document by applying [builder] to the current value, preserving untouched
     * fields. The baseline is the current value, or the type's defaults when the document is
     * absent. The read-modify-write runs under the document's write lock.
     *
     * Return the new value from the builder, idiomatically via `copy()`:
     * `update { current -> current.copy(name = "…") }`.
     */
    public fun update(builder: (T) -> T)

    /**
     * Removes the document and all of its field keys. A subsequent [get] returns `null`.
     */
    public fun delete()

    /**
     * Returns `true` when the document has at least one stored field key.
     */
    public fun exists(): Boolean

    /**
     * A cold [Flow] that emits the current value on collection, then the new value after each
     * committed write to this document. Emits `null` when the document is deleted or absent.
     * Emissions are conflated and happen only after the write is durably committed.
     */
    public fun flow(): Flow<T?>

    /**
     * A hot [StateFlow] over [flow], shared within [scope]. Holds the current value as its
     * initial state and updates after each committed write. Emits `null` when the document is
     * deleted or absent. Sharing stays active while there are active subscribers.
     */
    public fun stateFlow(scope: CoroutineScope): StateFlow<T?>
}

@OptIn(ExperimentalSerializationApi::class)
internal class DocumentImpl<T>(
    private val key: String,
    private val serializer: KSerializer<T>,
    private val storage: Storage,
    private val cbor: Cbor,
    private val changes: ChangeBus = ChangeBus(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Document<T> {

    private val lock = reentrantLock()

    override fun get(): T? = lock.withLock {
        if (!exists()) return null
        decodeDocument(key, serializer, storage, cbor)
    }

    override fun set(value: T): Unit = lock.withLock {
        clear()
        encodeDocument(key, value, serializer, storage, cbor)
        changes.emit(key)
    }

    override fun update(builder: (T) -> T): Unit = lock.withLock {
        val base = get() ?: defaults()
        set(builder(base))
    }

    override fun delete(): Unit = lock.withLock {
        clear()
        changes.emit(key)
    }

    override fun flow(): Flow<T?> =
        changes.keys
            .filter { it == key }
            .map { get() }
            .onStart { emit(get()) }
            .conflate()
            .flowOn(dispatcher)

    override fun stateFlow(scope: CoroutineScope): StateFlow<T?> =
        flow().stateIn(scope, SharingStarted.WhileSubscribed(), get())

    internal fun <V> readField(fieldName: String, default: V, serializer: KSerializer<V>): V = lock.withLock {
        val raw = storage.getBytes(Keys.field(key, fieldName)) ?: return default
        try {
            cbor.decodeFromByteArray(serializer, raw)
        } catch (cause: SerializationException) {
            throw DocumentDecodingException(key, fieldName, cause)
        } catch (cause: IllegalStateException) {
            throw DocumentDecodingException(key, fieldName, cause)
        } catch (cause: IllegalArgumentException) {
            throw DocumentDecodingException(key, fieldName, cause)
        }
    }

    internal fun <V> writeField(fieldName: String, value: V, serializer: KSerializer<V>): Unit = lock.withLock {
        storage.putBytes(Keys.field(key, fieldName), cbor.encodeToByteArray(serializer, value))
        changes.emit(key)
    }

    internal fun <V> fieldValues(fieldName: String, default: V, serializer: KSerializer<V>): Flow<V> =
        changes.keys
            .filter { it == key }
            .map { readField(fieldName, default, serializer) }
            .onStart { emit(readField(fieldName, default, serializer)) }
            .distinctUntilChanged()
            .conflate()
            .flowOn(dispatcher)

    private fun defaults(): T = decodeDocument(key, serializer, EmptyStorage, cbor)

    override fun exists(): Boolean = lock.withLock {
        storage.keys(Keys.prefix(key)).isNotEmpty()
    }

    private fun clear() {
        for (existing in storage.keys(Keys.prefix(key))) {
            storage.remove(existing)
        }
    }
}
