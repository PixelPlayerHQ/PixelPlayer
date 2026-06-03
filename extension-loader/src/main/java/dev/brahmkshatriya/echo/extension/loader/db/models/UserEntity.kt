package dev.brahmkshatriya.echo.extension.loader.db.models

import androidx.room.Entity
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.loader.utils.Serializer.toData
import dev.brahmkshatriya.echo.extension.loader.utils.Serializer.toJson

@Entity(primaryKeys = ["id", "type", "extId"])
data class UserEntity(
    val type: ExtensionType,
    val extId: String,
    val id: String,
    val data: String
) {
    val user by lazy { data.toData<User>() }

    companion object {
        fun User.toEntity(type: ExtensionType, clientId: String) =
            UserEntity(type, clientId, id, toJson())

        fun UserEntity.toCurrentUser() =
            CurrentUser(type, extId, id)
    }
}
