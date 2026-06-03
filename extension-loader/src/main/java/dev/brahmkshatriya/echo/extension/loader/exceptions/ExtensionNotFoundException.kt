package dev.brahmkshatriya.echo.extension.loader.exceptions

class ExtensionNotFoundException(
    val id: String?
) : Exception("Extension not found: $id")
