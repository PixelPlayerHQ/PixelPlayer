package dev.brahmkshatriya.echo.extension.loader.db.models

import androidx.room.Entity
import dev.brahmkshatriya.echo.common.models.ExtensionType

@Entity(primaryKeys = ["id", "type"])
data class ExtensionEntity(
    val id: String,
    val type : ExtensionType,
    val enabled : Boolean
)
