package com.nomemmurrakh.documents

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * A property delegate backed by the single decomposed key for [prop] on this document.
 *
 * Reading a never-set field returns [default]; writing updates only that field's key and emits
 * on the document's [Document.flow]. The field's value type is serialized with its standard
 * serializer.
 */
public inline fun <reified V> Document<*>.field(
    prop: KProperty1<*, V>,
    default: V,
): ReadWriteProperty<Any?, V> =
    fieldDelegate(this, prop.name, default, serializer())

@PublishedApi
internal fun <V> fieldDelegate(
    document: Document<*>,
    fieldName: String,
    default: V,
    serializer: KSerializer<V>,
): ReadWriteProperty<Any?, V> =
    DocumentFieldDelegate(document as DocumentImpl<*>, fieldName, default, serializer)

/**
 * A cold [Flow] of the value of the single field [prop] on this document.
 *
 * Emits the current value (or [default] if never set) on collection, then the new value each
 * time that field changes. A change to a different field of the same document does not emit.
 * The field's value type is deserialized with its standard serializer.
 */
public inline fun <reified V> Document<*>.fieldFlow(
    prop: KProperty1<*, V>,
    default: V,
): Flow<V> =
    fieldFlow(this, prop.name, default, serializer())

@PublishedApi
internal fun <V> fieldFlow(
    document: Document<*>,
    fieldName: String,
    default: V,
    serializer: KSerializer<V>,
): Flow<V> =
    (document as DocumentImpl<*>).fieldValues(fieldName, default, serializer)

/**
 * Writes [value] directly to the single decomposed key for [prop] on this document, with no
 * read of the rest of the document. Emits on the document's [Document.flow].
 */
public inline fun <T, reified V> Document<T>.update(prop: KProperty1<T, V>, value: V): Unit =
    updateField(this, prop.name, value, serializer())

@PublishedApi
internal fun <V> updateField(
    document: Document<*>,
    fieldName: String,
    value: V,
    serializer: KSerializer<V>,
) {
    (document as DocumentImpl<*>).writeField(fieldName, value, serializer)
}

private class DocumentFieldDelegate<V>(
    private val document: DocumentImpl<*>,
    private val fieldName: String,
    private val default: V,
    private val serializer: KSerializer<V>,
) : ReadWriteProperty<Any?, V> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): V =
        document.readField(fieldName, default, serializer)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        document.writeField(fieldName, value, serializer)
    }
}
