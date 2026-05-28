package com.theveloper.pixelplay.data.preferences

import android.content.Context
import androidx.annotation.StringRes
import com.theveloper.pixelplay.R

enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.setcat_language_system),
    ENGLISH("en", R.string.setcat_language_english),
    SPANISH("es", R.string.setcat_language_spanish),
    GERMAN("de", R.string.setcat_language_german),
    FRENCH("fr", R.string.setcat_language_french),
    RUSSIAN("ru", R.string.setcat_language_russian),
    CHINESE("zh-CN", R.string.setcat_language_chinese),
    INDONESIAN("in", R.string.setcat_language_indonesian),
    ITALIAN("it", R.string.setcat_language_italian);

    companion object {
        val supportedLanguageTags: Set<String> = values().map { it.tag }.toSet()

        fun getLanguageOptions(context: Context): Map<String, String> {
            val systemOption = SYSTEM.tag to context.getString(SYSTEM.labelRes)
            val otherOptions = values()
                .filter { it != SYSTEM }
                .map { it.tag to context.getString(it.labelRes) }
                .sortedBy { it.second.lowercase() }

            val result = LinkedHashMap<String, String>()
            result[systemOption.first] = systemOption.second
            for (option in otherOptions) {
                result[option.first] = option.second
            }
            return result
        }

        fun normalize(languageTag: String?): String {
            val normalized = languageTag?.trim() ?: return SYSTEM.tag
            return values().find { it.tag.equals(normalized, ignoreCase = true) }?.tag ?: SYSTEM.tag
        }
    }
}
