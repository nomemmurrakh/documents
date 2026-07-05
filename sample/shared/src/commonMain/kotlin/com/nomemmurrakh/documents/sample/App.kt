package com.nomemmurrakh.documents.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nomemmurrakh.documents.sample.cache.CacheScreen
import com.nomemmurrakh.documents.sample.queue.QueueScreen
import com.nomemmurrakh.documents.sample.reactive.ReactiveScreen
import com.nomemmurrakh.documents.sample.session.SessionScreen
import com.nomemmurrakh.documents.sample.settings.SettingsScreen

@Composable
fun App() {
    var screen: Screen by remember { mutableStateOf(Screen.Home) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize().safeContentPadding()) {
            when (screen) {
                Screen.Home -> HomeScreen(onSelect = { screen = it })
                Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                Screen.Session -> SessionScreen(onBack = { screen = Screen.Home })
                Screen.Cache -> CacheScreen(onBack = { screen = Screen.Home })
                Screen.Reactive -> ReactiveScreen(onBack = { screen = Screen.Home })
                Screen.SharedPersistence -> QueueScreen(onBack = { screen = Screen.Home })
            }
        }
    }
}
