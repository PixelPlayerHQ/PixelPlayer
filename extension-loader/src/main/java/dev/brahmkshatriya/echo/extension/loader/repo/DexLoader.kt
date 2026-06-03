package dev.brahmkshatriya.echo.extension.loader.repo

import android.content.Context
import android.os.Build
import dalvik.system.DexClassLoader
import dev.brahmkshatriya.echo.common.models.Metadata
import java.io.File
import java.lang.ref.WeakReference
import java.util.zip.ZipFile

class DexLoader(
    val metadata: Metadata,
    context: Context
) : DexClassLoader(
    metadata.path,
    context.cacheDir.absolutePath,
    extractNativeLibs(metadata, context),
    context.classLoader
) {
    private val loadedClasses = mutableMapOf<String, WeakReference<Class<*>>>()

    override fun loadClass(name: String): Class<*> {
        if (metadata.preservedPackages.any { name.startsWith(it) }) {
            val cached = loadedClasses[name]?.get()
            if (cached != null) return cached
        }

        val clazz = super.loadClass(name)

        if (metadata.preservedPackages.any { name.startsWith(it) }) {
            loadedClasses[name] = WeakReference(clazz)
        }

        return clazz
    }

    companion object {
        private fun extractNativeLibs(metadata: Metadata, context: Context): String? {
            val apkFile = File(metadata.path)
            if (!apkFile.exists()) return null

            val libDir = File(context.cacheDir, "extension_libs/${metadata.id}")
            if (!libDir.exists()) libDir.mkdirs()

            val supportedAbis = Build.SUPPORTED_ABIS
            
            try {
                ZipFile(apkFile).use { zip ->
                    val entries = zip.entries().asSequence().toList()
                    
                    // Find the best matching ABI
                    val bestAbi = supportedAbis.find { abi ->
                        entries.any { it.name.startsWith("lib/$abi/") }
                    } ?: return null

                    entries.filter { it.name.startsWith("lib/$bestAbi/") && it.name.endsWith(".so") }
                        .forEach { entry ->
                            val fileName = entry.name.substringAfterLast("/")
                            val outFile = File(libDir, fileName)
                            
                            // Only extract if doesn't exist or size mismatch
                            if (!outFile.exists() || outFile.length() != entry.size) {
                                zip.getInputStream(entry).use { input ->
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    return libDir.absolutePath
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}
