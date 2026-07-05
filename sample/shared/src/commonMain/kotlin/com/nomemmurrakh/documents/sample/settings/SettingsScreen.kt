package com.nomemmurrakh.documents.sample.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val repository = remember { SettingsRepository() }
    val theme by repository.themeFlow.collectAsStateWithLifecycle(initialValue = Theme.SYSTEM)
    val locale by repository.localeFlow.collectAsStateWithLifecycle(initialValue = "en-US")
    var onboardingDone by remember { mutableStateOf(repository.hasCompletedOnboarding) }
    var localeInput by remember(locale) { mutableStateOf(locale) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings & Preferences", style = MaterialTheme.typography.headlineSmall)

        Text("Theme (fieldFlow: $theme)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Theme.entries.forEach { candidate ->
                val selected = candidate == theme
                if (selected) {
                    Button(onClick = { repository.theme = candidate }) { Text(candidate.name) }
                } else {
                    OutlinedButton(onClick = { repository.theme = candidate }) { Text(candidate.name) }
                }
            }
        }

        Text("Locale (field delegate)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = localeInput,
            onValueChange = {
                localeInput = it
                repository.locale = it
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Onboarding complete", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = onboardingDone,
                onCheckedChange = {
                    onboardingDone = it
                    repository.hasCompletedOnboarding = it
                },
            )
        }

        Text(
            "Change the theme or locale, then relaunch the app on this platform — the value " +
                "persists because it's read from the same AppSettings document on startup.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
