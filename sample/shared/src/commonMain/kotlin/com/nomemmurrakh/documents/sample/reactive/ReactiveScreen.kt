package com.nomemmurrakh.documents.sample.reactive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomemmurrakh.documents.Document

@Composable
fun ReactiveScreen(onBack: () -> Unit) {
    val repository = remember { DownloadRepository() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Reactive UI State", style = MaterialTheme.typography.headlineSmall)
        Text(
            "A simulated download writes bytesDownloaded on a coroutine ticker via " +
                "update(DownloadState::bytesDownloaded, bytesSoFar). The progress bar below " +
                "re-renders purely by collecting the document's flow() — no polling.",
            style = MaterialTheme.typography.bodySmall,
        )

        DownloadProgressBar(repository.download)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { repository.start(scope) }) { Text("Start download") }
            OutlinedButton(onClick = { repository.reset() }) { Text("Reset") }
        }

        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun DownloadProgressBar(download: Document<DownloadState>) {
    val state by download.flow().collectAsStateWithLifecycle(initialValue = download.get())
    val progress = state?.let { it.bytesDownloaded.toFloat() / it.totalBytes } ?: 0f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        val bytes = state?.bytesDownloaded ?: 0
        val total = state?.totalBytes ?: 0
        val complete = state?.isComplete ?: false
        Text("$bytes / $total bytes${if (complete) " (complete)" else ""}")
    }
}
