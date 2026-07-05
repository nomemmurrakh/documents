package com.nomemmurrakh.documents.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onSelect: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Documents Sample", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Five use cases, each a real screen backed by the real Documents API.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(allUseCaseScreens) { screen ->
                UseCaseCard(screen, onClick = { onSelect(screen) })
            }
        }
    }
}

@Composable
private fun UseCaseCard(screen: Screen, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(screen.title, style = MaterialTheme.typography.titleMedium)
            Text(screen.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
