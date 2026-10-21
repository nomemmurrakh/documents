package com.nomemmurrakh.documents.sample.instances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomemmurrakh.documents.Document
import com.nomemmurrakh.documents.update
import kotlinx.coroutines.launch

@Composable
fun SharedInstancesScreen(onBack: () -> Unit) {
    val repository = remember { SharedInstancesRepository() }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Shared Instances", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Each section opens its own instance of the same document. A write in one section " +
                "shows up in the other, because both collect flow() from the same store.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        InstancePanel("Instance A", repository.instanceA, Modifier.weight(1f))
        HorizontalDivider()
        InstancePanel("Instance B", repository.instanceB, Modifier.weight(1f))
        HorizontalDivider()

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Instance A === Instance B: ${repository.areSameObject}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { repository.addFromBoth() } }) {
                    Text("Add 100 from both")
                }
                OutlinedButton(onClick = { repository.reset() }) { Text("Reset") }
            }
            Text(
                "Add 100 from both runs 100 updates on each instance at the same time. " +
                    "The count goes up by exactly 200, so no update is lost.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun InstancePanel(label: String, document: Document<SharedNote>, modifier: Modifier) {
    val note by document.flow().collectAsStateWithLifecycle(initialValue = document.get())
    var titleDraft by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text("title: ${note?.title?.ifEmpty { "(empty)" } ?: "(none)"}")
        Text("count: ${note?.count ?: 0}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { document.update { current -> current.copy(count = current.count + 1) } }) {
                Text("+1")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = titleDraft,
                onValueChange = { titleDraft = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { document.update(SharedNote::title, titleDraft) }) { Text("Set") }
        }
    }
}
