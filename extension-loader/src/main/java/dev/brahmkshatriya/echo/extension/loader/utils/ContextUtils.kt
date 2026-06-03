package dev.brahmkshatriya.echo.extension.loader.utils

import android.content.Context
import android.content.SharedPreferences

object ContextUtils {
    const val SETTINGS_NAME = "settings"
    fun Context.getSettings(): SharedPreferences = getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)!!
}
