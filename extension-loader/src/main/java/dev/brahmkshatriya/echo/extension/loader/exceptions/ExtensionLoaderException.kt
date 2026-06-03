package dev.brahmkshatriya.echo.extension.loader.exceptions

class ExtensionLoaderException(
    val loader: String,
    val source: String,
    override val cause: Throwable?
) : Exception(cause)
