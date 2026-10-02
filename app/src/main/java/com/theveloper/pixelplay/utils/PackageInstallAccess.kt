package com.theveloper.pixelplay.utils

import android.os.Build

/**
 * Package-install queries throw [SecurityException] on Android 8+ when
 * REQUEST_INSTALL_PACKAGES is missing from the manifest.
 */
object PackageInstallAccess {
    fun canRequestInstalls(sdkInt: Int = Build.VERSION.SDK_INT, query: () -> Boolean): Boolean {
        if (sdkInt < Build.VERSION_CODES.O) return true
        return runCatching(query).getOrDefault(false)
    }
}
