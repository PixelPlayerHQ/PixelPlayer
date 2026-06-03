package dev.brahmkshatriya.echo.extension.loader.exceptions

class RequiredExtensionsMissingException(
    val required: List<String>
) : Exception("Required extensions missing: $required")
