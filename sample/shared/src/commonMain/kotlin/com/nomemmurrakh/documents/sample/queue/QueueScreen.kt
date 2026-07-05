package com.nomemmurrakh.documents.sample.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.random.Random

@Composable
fun QueueScreen(onBack: () -> Unit) {
    val queue = remember { PendingRequestQueue() }
    val items by queue.observe().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Shared KMP Persistence", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This screen's code is 100% commonMain — no Android or iOS-specific branches were " +
                "written to make it work. Every screen in this app shares that property; this " +
                "one's whole use case is demonstrating it with an offline request queue.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                queue.enqueue(
                    OfflineQueueItem(
                        id = "req-${Random.nextInt(from = 1000, until = 9999)}",
                        endpoint = "/sync",
                        payloadJson = "{}",
                    ),
                )
            }) { Text("Enqueue mock request") }

            OutlinedButton(onClick = { queue.clear() }) { Text("Clear queue") }
        }

        Text("${items.size} pending", style = MaterialTheme.typography.titleMedium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${item.id} -> ${item.endpoint}", style = MaterialTheme.typography.bodyMedium)
                        Text("attempts: ${item.attempts}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
