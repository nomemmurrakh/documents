package com.nomemmurrakh.documents.sample.session

import com.nomemmurrakh.documents.Document
import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable

@Serializable
private data class SessionKeyMaterial(val bytes: ByteArray)

private object SessionKey {
    val value: AES.GCM.Key by lazy {
        val algorithm = CryptographyProvider.Default.get(AES.GCM)
        val keyStore = Documents.collection("session_key")
        val keyDocument = keyStore.document<SessionKeyMaterial>("current")
        val stored = keyDocument.get()
        if (stored != null) {
            algorithm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, stored.bytes)
        } else {
            val generated = algorithm.keyGenerator().generateKeyBlocking()
            val encoded = generated.encodeToByteArrayBlocking(AES.Key.Format.RAW)
            keyDocument.set(SessionKeyMaterial(encoded))
            generated
        }
    }
}

class SessionRepository {
    private val key: AES.GCM.Key = SessionKey.value

    private val sessionStore = Documents.collection("session") {
        decorators = listOf(AesGcmFieldDecorator(key))
    }
    val session: Document<Session> = sessionStore.document("current")

    fun signIn(userId: String, displayName: String, authToken: String) {
        session.set(Session(userId = userId, displayName = displayName, authToken = authToken))
    }

    fun signOut() {
        session.delete()
    }
}
