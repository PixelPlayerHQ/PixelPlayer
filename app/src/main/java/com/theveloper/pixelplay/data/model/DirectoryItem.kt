package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class DirectoryItem(
    val path: String,
    val isAllowed: Boolean
) {
    val displayName: String
        get() = File(path).name.ifEmpty { path } // Muestra el nombre de la carpeta o el path si es la raíz
}
