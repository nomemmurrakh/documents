package com.nomemmurrakh.documents.sample.settings

import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import com.nomemmurrakh.documents.field
import com.nomemmurrakh.documents.fieldFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class Theme { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    val theme: Theme = Theme.SYSTEM,
    val locale: String = "en-US",
    val hasCompletedOnboarding: Boolean = false,
)

class SettingsRepository {
    private val doc = Documents.document<AppSettings>("app-settings")

    var theme: Theme by doc.field(AppSettings::theme, default = Theme.SYSTEM)
    var locale: String by doc.field(AppSettings::locale, default = "en-US")
    var hasCompletedOnboarding: Boolean by doc.field(AppSettings::hasCompletedOnboarding, default = false)

    val themeFlow: Flow<Theme> = doc.fieldFlow(AppSettings::theme, default = Theme.SYSTEM)
    val localeFlow: Flow<String> = doc.fieldFlow(AppSettings::locale, default = "en-US")
}
