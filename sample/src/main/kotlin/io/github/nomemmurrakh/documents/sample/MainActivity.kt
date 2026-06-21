package io.github.nomemmurrakh.documents.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nomemmurrakh.documents.Documents
import io.github.nomemmurrakh.documents.document
import io.github.nomemmurrakh.documents.fieldFlow
import kotlinx.serialization.Serializable

// A nested @Serializable type — stored as one sub-blob under its field's key.
@Serializable
data class Player(val name: String = "", val hp: Int = 100)

@Serializable
data class GameSave(
    val level: Int = 1,
    val coins: Int = 0,
    val player: Player = Player(),
)

// Lives in a separate "cache" collection (its own MMKV file) to prove isolation.
@Serializable
data class Draft(val text: String = "", val edits: Int = 0)

/**
 * One screen that exercises every claim in the README so a single run is a real smoke test:
 * replace vs. update, get/delete/exists, per-field flow (decomposition), nested @Serializable,
 * and a separate collection that stays isolated from the default store.
 */
@Composable
fun DemoScreen() {
    // --- Default store ---------------------------------------------------------------
    val save = remember { Documents.document<GameSave>("slot-1") }
    val current by save.flow().collectAsStateWithLifecycle(initialValue = save.get())

    // Per-field flow — proves a single decomposed field reacts on its own.
    val coins by save.fieldFlow(GameSave::coins, default = 0)
        .collectAsStateWithLifecycle(initialValue = 0)

    // --- A separate collection (its own MMKV file) -----------------------------------
    val cache = remember { Documents.collection("cache") }
    val draftDoc = remember { cache.document<Draft>("draft") }
    val draft by draftDoc.flow().collectAsStateWithLifecycle(initialValue = draftDoc.get())

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Documents — feature smoke test", style = MaterialTheme.typography.titleLarge)

        // --- Section: default document, replace vs. update ---------------------------
        Section("Default document  (slot-1)") {
            Mono("get()    = ${current ?: "null (absent)"}")
            Mono("exists() = ${save.exists()}")
            Mono("coins (fieldFlow) = $coins")

            ButtonRow {
                Button(onClick = {
                    // Full replace — a whole object is given.
                    save.set(GameSave(level = 1, coins = 0, player = Player("Mara", hp = 100)))
                }) { Text("Reset (set value)") }

                Button(onClick = {
                    // Update — builder runs over the persisted value, copy-style.
                    save.set { copy(level = level + 1) }
                }) { Text("Level +1 (update)") }
            }

            ButtonRow {
                Button(onClick = {
                    // Writes only the coins field's key (decomposition).
                    save.set { copy(coins = coins + 50) }
                }) { Text("Coins +50") }

                Button(onClick = {
                    // Touch a nested @Serializable sub-blob.
                    save.set { copy(player = player.copy(hp = (player.hp - 10).coerceAtLeast(0))) }
                }) { Text("Player hp -10") }
            }

            ButtonRow {
                OutlinedButton(onClick = { save.delete() }) { Text("delete()") }
            }
        }

        // --- Section: a separate collection, isolated from the default store ---------
        Section("Collection  cache/draft  (separate file)") {
            Mono("get()    = ${draft ?: "null (absent)"}")
            Mono("exists() = ${draftDoc.exists()}")

            ButtonRow {
                Button(onClick = {
                    draftDoc.set { copy(text = "hello", edits = edits + 1) }
                }) { Text("Edit draft") }

                OutlinedButton(onClick = { draftDoc.delete() }) { Text("Clear draft") }
            }
            Text(
                "Editing the draft must NOT change the slot-1 readout above — that proves " +
                    "the collection is a separate file.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoScreen()
                }
            }
        }
    }
}
