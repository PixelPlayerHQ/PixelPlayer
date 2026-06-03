package com.theveloper.pixelplay.extensions.core

import javax.inject.Inject
import javax.inject.Singleton
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader

/**
 * Compatibility wrapper exposing the older in-app `ExtensionEngine` API
 * while delegating to the real `ExtensionLoader` from Echo.
 */
@Singleton
class ExtensionEngine @Inject constructor(
    private val loader: ExtensionLoader
) {
    // Keep the same property name the app expects.
    val extensions = loader.all

    fun getExtensionById(id: String) = extensions.value.find { it.metadata.id == id }

    fun refreshAndInit() {
        // No-op for now; the real loader manages its own repository flows.
        // Keep this for binary compatibility with previous calls.
    }
}
