package dev.brahmkshatriya.echo.extension.loader.exceptions

class InvalidExtensionListException(
    val required: List<String>
) : Exception("Required extensions missing: $required")
