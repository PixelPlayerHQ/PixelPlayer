package com.theveloper.pixelplay.data.preferences

import android.content.Context
import com.theveloper.pixelplay.R  // Add this import
import android.content.Context

object AppLanguage {
    const val SYSTEM = ""
    const val ENGLISH = "en"
    const val SPANISH = "es"
    const val FRENCH = "fr"
    const val RUSSIAN = "ru"

    val supportedLanguageTags: Set<String> = setOf(SYSTEM, ENGLISH, SPANISH, FRENCH, RUSSIAN)

    fun getLanguageOptions(context: Context): Map<String, String> {
        return mapOf(
            SYSTEM to context.getString(R.string.setcat_language_system),
            ENGLISH to context.getString(R.string.setcat_language_english),
            SPANISH to context.getString(R.string.setcat_language_spanish),
            FRENCH to context.getString(R.string.setcat_language_french),
            RUSSIAN to context.getString(R.string.setcat_language_russian)
        )
    }

    fun normalize(languageTag: String): String {
        val normalized = languageTag.trim().lowercase()
        return if (normalized in supportedLanguageTags) normalized else SYSTEM
    }
}
