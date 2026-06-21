package io.github.nomemmurrakh.documents.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nomemmurrakh.documents.Documents
import io.github.nomemmurrakh.documents.MergeStrategy
import io.github.nomemmurrakh.documents.document
import kotlinx.serialization.Serializable

@Serializable
data class Settings(val theme: String = "system", val launchCount: Int = 0)

@Composable
fun SettingsScreen() {
    val store = remember { Documents.create("sample") }
    val doc = remember { store.document<Settings>("settings") }
    val settings by doc.flow().collectAsStateWithLifecycle(initialValue = doc.get())

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("theme = ${settings?.theme ?: "system"}", style = MaterialTheme.typography.titleLarge)
        Text("launches = ${settings?.launchCount ?: 0}")
        Button(onClick = { doc.set(MergeStrategy.UPDATE) { copy(theme = "dark") } }) {
            Text("Use dark theme")
        }
        Button(onClick = { doc.set(MergeStrategy.UPDATE) { copy(launchCount = launchCount + 1) } }) {
            Text("Increment launches")
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }
            }
        }
    }
}
