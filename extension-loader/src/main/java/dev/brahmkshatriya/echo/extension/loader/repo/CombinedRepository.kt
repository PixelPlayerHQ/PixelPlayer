package dev.brahmkshatriya.echo.extension.loader.repo

import android.content.Context
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.models.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.io.File

class CombinedRepository(
    scope: CoroutineScope,
    context: Context,
    fileIgnoreFlow: Flow<File?>,
    extensionParser: ExtensionParser,
    vararg builtIns: Pair<Metadata, Lazy<ExtensionClient>>
) : ExtensionRepository {

    private val list = builtIns.map { Result.success(it) }
    private val appRepository = AppRepository(scope, context, extensionParser)
    private val fileRepository = FileRepository(context, extensionParser, fileIgnoreFlow)

    override val flow = fileRepository.flow.combine(appRepository.flow) { file, app ->
        if (app == null) return@combine null
        list + file + app
    }.stateIn(scope, SharingStarted.Lazily, list)

    override suspend fun loadExtensions() = flow.first { it != null } ?: list
}
