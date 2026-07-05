package com.nomemmurrakh.documents.sample.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomemmurrakh.documents.DocumentDecodingException
import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.random.Random

@Composable
fun SessionScreen(onBack: () -> Unit) {
    val repository = remember { SessionRepository() }
    val current by repository.session.flow().collectAsStateWithLifecycle(initialValue = repository.session.get())
    var decryptionProof by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Session & User State", style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val state = current
                if (state != null) {
                    Text("Signed in as ${state.displayName}", style = MaterialTheme.typography.titleMedium)
                    Text("userId: ${state.userId}", fontFamily = FontFamily.Monospace)
                    Text("authToken: ${state.authToken}", fontFamily = FontFamily.Monospace)
                } else {
                    Text("Signed out", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                repository.signIn(
                    userId = "u_${Random.nextInt(1000, 9999)}",
                    displayName = "Mara",
                    authToken = "token-${Random.nextInt(100000, 999999)}",
                )
                decryptionProof = null
            }) { Text("Sign in") }

            OutlinedButton(onClick = {
                repository.signOut()
                decryptionProof = null
            }) { Text("Sign out") }
        }

        OutlinedButton(onClick = {
            decryptionProof = tryDecryptWithWrongKey(repository)
        }) { Text("Prove authToken is encrypted at rest") }

        decryptionProof?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "authToken is wrapped by an AesGcmFieldDecorator (FieldDecorator + cryptography-kotlin's " +
                "AES.GCM) before it reaches storage. Sign in, then tap the button above: a second " +
                "document opened with a different AES key fails to decrypt the same stored bytes, " +
                "proving they are real ciphertext, not the plaintext token.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}

private fun tryDecryptWithWrongKey(repository: SessionRepository): String {
    if (repository.session.get() == null) return "Sign in first."

    val wrongKey = CryptographyProvider.Default.get(AES.GCM).keyGenerator().generateKeyBlocking()
    val wrongCollection = Documents.collection("session") {
        decorators = listOf(AesGcmFieldDecorator(wrongKey))
    }
    val wrongSession = wrongCollection.document<Session>("current")

    return try {
        wrongSession.get()
        "Unexpected: decrypted with the wrong key. This should not happen."
    } catch (e: DocumentDecodingException) {
        "Confirmed: a different AES key fails to decrypt the stored authToken " +
            "(${e.message?.take(60)}...) — the bytes on disk are ciphertext."
    }
}
