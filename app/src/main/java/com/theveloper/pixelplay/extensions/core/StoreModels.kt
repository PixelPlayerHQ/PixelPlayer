package com.theveloper.pixelplay.extensions.core

import kotlinx.serialization.Serializable

@Serializable
data class RemoteExtension(
    val id: String,
    val name: String,
    val type: String,
    val subtitle: String = "",
    val iconUrl: String = "",
    val updateUrl: String = "",
    val downloadUrl: String? = null,
    val version: String = "1.0"
)

enum class ExtensionStatus {
    AVAILABLE,
    DOWNLOADING,
    INSTALLING,
    INSTALLED,
    UPDATE_AVAILABLE
}

data class ExtensionStoreItem(
    val remote: RemoteExtension,
    val status: ExtensionStatus,
    val progress: Float = 0f,
    val localVersion: String? = null
)
