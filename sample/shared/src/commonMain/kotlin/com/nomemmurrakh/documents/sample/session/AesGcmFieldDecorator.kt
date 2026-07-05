package com.nomemmurrakh.documents.sample.session

import com.nomemmurrakh.documents.FieldDecorator
import dev.whyoleg.cryptography.algorithms.AES

class AesGcmFieldDecorator(
    private val key: AES.GCM.Key,
) : FieldDecorator {

    private val cipher = key.cipher()

    override fun wrap(fieldName: String, bytes: ByteArray): ByteArray =
        cipher.encryptBlocking(
            plaintext = bytes,
            associatedData = fieldName.encodeToByteArray(),
        )

    override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray =
        cipher.decryptBlocking(
            ciphertext = bytes,
            associatedData = fieldName.encodeToByteArray(),
        )
}
