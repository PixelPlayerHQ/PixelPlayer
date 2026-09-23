pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.FaceOnLive")
                includeGroup("com.github.philburk")
                includeGroup("com.github.racra")
                includeGroup("com.github.tdlibx")
                includeGroup("com.github.teamnewpipe")
                includeGroup("com.github.TeamNewPipe")
                includeGroupByRegex("com\\.github\\.[Tt]eam[Nn]ew[Pp]ipe(\\..*)?")
            }
        }
    }
}

rootProject.name = "PixelPlay"
include(":app")
include(":shared")
include(":wear")
include(":baselineprofile")
