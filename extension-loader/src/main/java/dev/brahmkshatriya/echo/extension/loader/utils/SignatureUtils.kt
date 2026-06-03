package dev.brahmkshatriya.echo.extension.loader.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SignatureUtils {

    // Example trusted SHA-256 hashes of developer certificates
    private val TRUSTED_SIGNATURES = setOf(
        "61:1E:5E:2B:B4:4D:30:26:6A:B9:4C:E6:4D:4E:09:A6:22:98:C3:9C:5F:9E:0B:4D:A2:7D:6D:77:9D:68:53:E7", // Official Echo Key
        "8F:8B:6A:E5:6B:8F:A1:02:4B:B2:76:83:8B:3D:28:B4:9D:E1:92:09:3A:C4:B9:9E:E2:20:9E:22:91:02:8B:6A"  // Official PixelPlayer Key
    )

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("Deprecation")
    fun isTrusted(context: Context, filePath: String): Boolean {
        val pm = context.packageManager
        val packageInfo: PackageInfo = pm.getPackageArchiveInfo(
            filePath,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        ) ?: return false

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        } ?: return false

        for (sig in signatures) {
            val sha256 = getSignatureSha256(sig.toByteArray())
            if (TRUSTED_SIGNATURES.contains(sha256)) return true
        }

        return false
    }

    private fun getSignatureSha256(signature: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
