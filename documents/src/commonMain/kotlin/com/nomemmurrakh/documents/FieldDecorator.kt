package com.nomemmurrakh.documents

/**
 * A bytes-in/bytes-out transform applied to one field's already-CBOR-encoded value.
 *
 * A [FieldDecorator] sits between [Document] and the CBOR/decomposition layer, letting
 * per-field behavior such as encryption, compression, checksums, or logging be added without
 * exposing the document's storage keys or on-disk format. [wrap] runs on write, immediately
 * before the encoded bytes reach storage; [unwrap] runs on read, immediately after the bytes
 * are read from storage and before they are decoded.
 *
 * When more than one decorator is configured, they run in list order on [wrap] and reverse
 * order on [unwrap], so the decorator that ran last on write is the first to run on read.
 *
 * [unwrap] should throw [kotlinx.serialization.SerializationException],
 * [IllegalStateException], or [IllegalArgumentException] to report a failure (for example, a
 * wrong key or corrupted input); it surfaces to callers as [DocumentDecodingException].
 *
 * An encryption-flavored implementation should bind its output to [fieldName] as associated
 * data (for example, `fieldName.encodeToByteArray()`), so a ciphertext moved from one field's
 * key to another no longer decrypts.
 */
public interface FieldDecorator {

    /** Transforms [bytes] for [fieldName] before they are written to storage. */
    public fun wrap(fieldName: String, bytes: ByteArray): ByteArray

    /** Reverses [wrap] for [fieldName], transforming bytes read from storage back to CBOR. */
    public fun unwrap(fieldName: String, bytes: ByteArray): ByteArray
}

internal fun applyWrap(decorators: List<FieldDecorator>, fieldName: String, bytes: ByteArray): ByteArray =
    decorators.fold(bytes) { acc, decorator -> decorator.wrap(fieldName, acc) }

internal fun applyUnwrap(decorators: List<FieldDecorator>, fieldName: String, bytes: ByteArray): ByteArray =
    decorators.foldRight(bytes) { decorator, acc -> decorator.unwrap(fieldName, acc) }
