package com.nomemmurrakh.documents.sample

sealed interface Screen {
    val title: String
    val description: String

    data object Home : Screen {
        override val title: String = "Documents Sample"
        override val description: String = ""
    }

    data object Settings : Screen {
        override val title: String = "Settings & Preferences"
        override val description: String = "Typed, observable app settings."
    }

    data object Session : Screen {
        override val title: String = "Session & User State"
        override val description: String = "Sign-in/out, with an encrypted auth token."
    }

    data object Cache : Screen {
        override val title: String = "Caches & Drafts"
        override val description: String = "A draft that survives relaunch, in its own store."
    }

    data object Reactive : Screen {
        override val title: String = "Reactive UI State"
        override val description: String = "A simulated download driving a live progress bar."
    }

    data object SharedPersistence : Screen {
        override val title: String = "Shared KMP Persistence"
        override val description: String = "One offline queue, identical code on both platforms."
    }
}

val allUseCaseScreens: List<Screen> = listOf(
    Screen.Settings,
    Screen.Session,
    Screen.Cache,
    Screen.Reactive,
    Screen.SharedPersistence,
)
