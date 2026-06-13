package com.theveloper.pixelplay.data.preferences

import android.content.Context
import androidx.annotation.StringRes
import com.theveloper.pixelplay.R

enum class AppLanguage(val tag: String, val nativeName: String, @StringRes val labelRes: Int?) {
    SYSTEM("", "", R.string.settings_language_system),
    ENGLISH("en", "English", null),
    GERMAN("de", "Deutsch", null),
    SPANISH("es", "Español", null),
    FRENCH("fr", "Français", null),
    INDONESIAN("in", "Bahasa Indonesia", null),
    ITALIAN("it", "Italiano", null),
    KOREAN("ko", "한국어", null),
    NORWEGIAN_BOKMAL("nb", "Norsk bokmål", null),
    RUSSIAN("ru", "Русский", null),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文", null),
    TURKISH("tr", "Türkçe", null),
    ARABIC("ar", "العربية", null);

    companion object {
        val supportedLanguageTags: Set<String> = values().map { it.tag }.toSet()

        fun getLanguageOptions(context: Context): Map<String, String> {
            val systemOption = SYSTEM.tag to (SYSTEM.labelRes?.let { context.getString(it) } ?: "")
            val otherOptions = values()
                .filter { it != SYSTEM }
                .map { it.tag to it.nativeName }
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
