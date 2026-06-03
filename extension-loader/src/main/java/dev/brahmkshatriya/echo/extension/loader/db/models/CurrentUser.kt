package dev.brahmkshatriya.echo.extension.loader.db.models

import androidx.room.Entity
import dev.brahmkshatriya.echo.common.models.ExtensionType

@Entity(primaryKeys = ["type", "extId"])
data class CurrentUser(
    val type : ExtensionType,
    val extId: String,
    val userId: String?
)
