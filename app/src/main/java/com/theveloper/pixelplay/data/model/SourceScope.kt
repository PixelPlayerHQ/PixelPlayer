package com.theveloper.pixelplay.data.model

sealed interface SourceScope {
    data object All : SourceScope
    data object Local : SourceScope
    data class Extension(
        val extensionId: String
    ) : SourceScope
}

fun SourceScope.toFilterMode(): Int = when (this) {
    SourceScope.All -> 0
    SourceScope.Local -> 1
    is SourceScope.Extension -> 2
}

fun SourceScope.toExtensionId(): String? = when (this) {
    is SourceScope.Extension -> this.extensionId
    else -> null
}
