package com.theveloper.pixelplay.presentation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.pixelplay.R

enum class SettingsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    LIBRARY(
        id = "library",
        title = "Music Management",
        subtitle = "Manage folders, refresh library, parsing options",
        icon = Icons.Rounded.LibraryMusic
    ),
    APPEARANCE(
        id = "appearance",
        title = "Appearance",
        subtitle = "Themes, layout, and visual styles",
        icon = Icons.Rounded.Palette
    ),
    PLAYBACK(
        id = "playback",
        title = "Playback",
        subtitle = "Audio behavior, crossfade, and background play",
        icon = Icons.Rounded.MusicNote // Using MusicNote again or maybe PlayCircle if available
    ),
    BEHAVIOR(
        id = "behavior",
        title = "Behavior",
        subtitle = "Gestures, haptics, and navigation behavior",
        iconRes = R.drawable.rounded_touch_app_24
    ),
    AI_INTEGRATION(
        id = "ai",
        title = "AI Integration (β)",
        subtitle = "AI providers, API keys, and model settings",
        iconRes = R.drawable.gemini_ai
    ),
    BACKUP_RESTORE(
        id = "backup_restore",
        title = "Backup & Restore",
        subtitle = "Export and recover your personal app data",
        iconRes = R.drawable.rounded_upload_file_24
    ),
    DEVELOPER(
        id = "developer",
        title = "Developer Options",
        subtitle = "Experimental features and debugging",
        icon = Icons.Rounded.DeveloperMode
    ),
    EQUALIZER(
        id = "equalizer",
        title = "Equalizer",
        subtitle = "Adjust audio frequencies and presets",
        icon = Icons.Rounded.GraphicEq
    ),
    DEVICE_CAPABILITIES(
        id = "device_capabilities",
        title = "Device Capabilities",
        subtitle = "Audio specs, codecs, and decoder info",
        icon = Icons.Rounded.DeveloperBoard // Placeholder, maybe Memory or SettingsInputComponent
    ),
    ABOUT(
        id = "about",
        title = "About",
        subtitle = "App info, version, and credits",
        icon = Icons.Rounded.Info
    );

    companion object {
        fun fromId(id: String): SettingsCategory? = entries.find { it.id == id }
    }
}
