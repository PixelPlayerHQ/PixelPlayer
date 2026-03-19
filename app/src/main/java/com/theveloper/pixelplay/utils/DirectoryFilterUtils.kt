package com.theveloper.pixelplay.utils

/**
 * Shared helper for computing allowed parent directories by applying blocked rules.
 */
object DirectoryFilterUtils {
    suspend fun computeAllowedParentDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>,
        getAllParentDirs: suspend () -> List<String>,
        normalizePath: (String) -> String
    ): Pair<List<String>, Boolean> {
        if (blockedDirs.isEmpty()) return Pair(emptyList(), false)

        val resolver = DirectoryRuleResolver(
            allowedDirs.map(normalizePath).toSet(),
            blockedDirs.map(normalizePath).toSet()
        )
        val allParentDirs = getAllParentDirs()
        val allowedParentDirs = allParentDirs.filter { parentDir ->
            !resolver.isBlocked(normalizePath(parentDir))
        }
        return Pair(allowedParentDirs, true)
    }
}
