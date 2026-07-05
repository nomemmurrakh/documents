package com.nomemmurrakh.documents.sample.session

import com.nomemmurrakh.documents.Document
import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES

class SessionRepository {
    private val provider = CryptographyProvider.Default
    private val key: AES.GCM.Key = provider.get(AES.GCM).keyGenerator().generateKeyBlocking()

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
