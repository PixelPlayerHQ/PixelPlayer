plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("plugin.serialization")
}

android {
    namespace = "com.theveloper.pixelplay.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 29 // Must match app module's minSdk; shared code is pure DTOs with no platform APIs
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
    buildTypes {
        create("benchmarkRelease") {
        }
        create("nonMinifiedRelease") {
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
