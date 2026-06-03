package dev.brahmkshatriya.echo.extension.loader.repo

import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.ImportType
import dev.brahmkshatriya.echo.common.models.Metadata
import dev.brahmkshatriya.echo.extension.loader.exceptions.ExtensionLoaderException
import dev.brahmkshatriya.echo.extension.loader.utils.ShaUtils.getSha256
import dev.brahmkshatriya.echo.extension.loader.utils.SignatureUtils
import java.io.File
import java.util.WeakHashMap

class ExtensionParser(
    private val context: Context,
) {

    fun getAllDynamically(
        type: ImportType,
        map: WeakHashMap<String, Pair<String, Result<Pair<Metadata, Lazy<ExtensionClient>>>>>,
        files: List<File>
    ): List<Result<Pair<Metadata, Lazy<ExtensionClient>>>> {
        val new = files.associate {
            val sha = runCatching { getSha256(it) }.getOrNull().orEmpty()
            val entry = map[it.absolutePath]
            val value = if (entry != null && entry.first == sha) entry
            else sha to parse(it, type)
            it.absolutePath to value
        }
        map.clear()
        map.putAll(new)
        return map.values.map { it.second }
    }

    private fun parse(source: File, importType: ImportType) = runCatching {
        runCatching {
            val metadata = parseManifest(source, importType)
            val lazy = lazy { loadFrom(metadata) }
            metadata to lazy
        }.getOrElse {
            throw ExtensionLoaderException(javaClass.simpleName, source.toString(), it)
        }
    }


    fun parseManifest(file: File, importType: ImportType): Metadata {
        if (!SignatureUtils.isTrusted(context, file.absolutePath)) {
            error("Untrusted extension: ${file.name}. APK signature verification failed.")
        }

        val packageInfo =
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
                ?: error("Failed to get package info for ${file.absolutePath}")
        val metadata = packageInfo.applicationInfo!!.metaData!!
        val type = packageInfo.reqFeatures!!.toExtensionType()
        fun getOrNull(key: String) = metadata.getString(key)?.takeIf { it.isNotBlank() }
        fun get(key: String) = getOrNull(key)
            ?: error("$key not found in Metadata for ${packageInfo.packageName}")
        return Metadata(
            path = file.absolutePath,
            preservedPackages = getOrNull("preserved_packages")
                .orEmpty().split(",").mapNotNull { it.trim().ifEmpty { null } },
            className = get("class"),
            importType = importType,
            type = type,
            id = get("id"),
            version = get("version"),
            icon = getOrNull("icon_url")?.toImageHolder(),
            name = get("name"),
            description = get("description"),
            author = get("author"),
            authorUrl = getOrNull("author_url"),
            repoUrl = getOrNull("repo_url"),
            updateUrl = getOrNull("update_url"),
            isEnabled = metadata.getBoolean("enabled", true)
        )
    }

    private fun loadFrom(metadata: Metadata): ExtensionClient {
        val dexLoader = DexLoader(metadata, context)
        val clazz = dexLoader.loadClass(metadata.className)
        return clazz.getConstructor().newInstance() as ExtensionClient
    }

    companion object {

        @Suppress("Deprecation")
        val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
                PackageManager.GET_META_DATA or
                PackageManager.GET_SIGNATURES or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0

        const val FEATURE = "dev.brahmkshatriya.echo."
        private fun Array<FeatureInfo>.toExtensionType(): ExtensionType {
            val feature = first { it.name.startsWith(FEATURE) }
            val type = feature.name.substringAfter(FEATURE)
            return ExtensionType.entries.first { it.feature == type }
        }


    }
}
