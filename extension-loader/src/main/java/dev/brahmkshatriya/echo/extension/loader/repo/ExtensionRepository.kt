package dev.brahmkshatriya.echo.extension.loader.repo

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.models.Metadata
import kotlinx.coroutines.flow.Flow

interface ExtensionRepository {
    val flow: Flow<List<Result<Pair<Metadata, Lazy<ExtensionClient>>>>?>
    suspend fun loadExtensions(): List<Result<Pair<Metadata, Lazy<ExtensionClient>>>>
}
