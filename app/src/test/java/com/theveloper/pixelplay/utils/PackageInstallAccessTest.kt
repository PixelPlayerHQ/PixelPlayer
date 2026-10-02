package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PackageInstallAccessTest {

    @Test
    fun preOreo_doesNotQueryAndAllowsInstall() {
        val allowed = PackageInstallAccess.canRequestInstalls(sdkInt = 25) {
            error("must not query canRequestPackageInstalls before Oreo")
        }
        assertThat(allowed).isTrue()
    }

    @Test
    fun oreoPlus_granted_returnsTrue() {
        assertThat(PackageInstallAccess.canRequestInstalls(sdkInt = 26) { true }).isTrue()
        assertThat(PackageInstallAccess.canRequestInstalls(sdkInt = 36) { true }).isTrue()
    }

    @Test
    fun oreoPlus_denied_returnsFalse() {
        assertThat(PackageInstallAccess.canRequestInstalls(sdkInt = 36) { false }).isFalse()
    }

    @Test
    fun missingManifestPermission_doesNotCrash() {
        val allowed = PackageInstallAccess.canRequestInstalls(sdkInt = 36) {
            throw SecurityException("Need to declare android.permission.REQUEST_INSTALL_PACKAGES to call this api")
        }
        assertThat(allowed).isFalse()
    }
}
