package com.theveloper.pixelplay.data.github

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GitHubReleaseUpdateServiceTest {

    @Test
    fun releasePage_allowsThisRepoOnly() {
        assertThat(
            GitHubReleaseUpdateService.isAllowedReleasePage(
                "https://github.com/kibetmasi/PixelPlayer/releases/tag/vcontinuous-20260928-23f4e155",
            ),
        ).isTrue()
        assertThat(
            GitHubReleaseUpdateService.isAllowedReleasePage("https://evil.example/releases"),
        ).isFalse()
        assertThat(
            GitHubReleaseUpdateService.isAllowedReleasePage("http://github.com/kibetmasi/PixelPlayer/releases"),
        ).isFalse()
    }

    @Test
    fun apkDownload_allowsThisRepoApkOnly() {
        assertThat(
            GitHubReleaseUpdateService.isAllowedApkDownload(
                "https://github.com/kibetmasi/PixelPlayer/releases/download/v1/app-arm64-v8a-release.apk",
            ),
        ).isTrue()
        assertThat(
            GitHubReleaseUpdateService.isAllowedApkDownload(
                "https://github.com/kibetmasi/PixelPlayer/releases/download/v1/notes.txt",
            ),
        ).isFalse()
        assertThat(
            GitHubReleaseUpdateService.isAllowedApkDownload(
                "https://github.com/someone/else/releases/download/v1/app.apk",
            ),
        ).isFalse()
    }
}
