package com.nomemmurrakh.documents.sample.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomemmurrakh.documents.sample.settings.SettingsRepository
import kotlinx.coroutines.delay

@Composable
fun CacheScreen(onBack: () -> Unit) {
    val repository = remember { CacheRepository() }
    val settingsRepository = remember { SettingsRepository() }

    val syncState by repository.syncState.flow().collectAsStateWithLifecycle(initialValue = repository.syncState.get())
    val theme by settingsRepository.themeFlow.collectAsStateWithLifecycle(initialValue = settingsRepository.theme)

    var draftBody by remember { mutableStateOf(repository.draft.get()?.body ?: "") }

    LaunchedEffect(draftBody) {
        delay(300)
        repository.draft.update { current -> current.copy(body = draftBody) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Caches & Drafts", style = MaterialTheme.typography.headlineSmall)

        Text("Draft (debounced update, restored on screen entry)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = draftBody,
            onValueChange = { draftBody = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sync state", style = MaterialTheme.typography.titleMedium)
                Text("lastSyncedAtEpochMs: ${syncState?.lastSyncedAtEpochMs ?: 0}")
                Text("lastSyncedEtag: ${syncState?.lastSyncedEtag ?: "(none)"}")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { repository.simulateSync() }) { Text("Simulate sync") }
            OutlinedButton(onClick = {
                repository.clearCache()
                draftBody = ""
            }) { Text("Clear cache") }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Isolation check", style = MaterialTheme.typography.titleMedium)
                Text("Settings theme (different collection): $theme")
                Text(
                    "Clearing the cache above never changes this value — cache and app-settings " +
                        "are separate Documents collections, each its own MMKV file.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
