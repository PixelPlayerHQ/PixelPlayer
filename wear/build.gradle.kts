plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.theveloper.pixelplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.theveloper.pixelplay"
        minSdk = 30
        targetSdk = 37
        versionCode = (project.findProperty("APP_VERSION_CODE") as String).toInt()
        versionName = project.findProperty("APP_VERSION_NAME") as String
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=com.google.android.horologist.annotations.ExperimentalHorologistApi")
    }
}

dependencies {
    implementation(project(":shared"))

    // Wear OS & Horologist
    implementation(libs.bundles.wear)
    implementation(libs.bundles.horologist)

    // Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // DI & Navigation
    implementation(libs.bundles.hilt)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Storage & Media
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.mediarouter)

    // UI Utilities
    implementation(libs.coil.compose)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)

    constraints {
        // Fix vulnerabilities in transitive dependencies
        implementation(libs.netty.common)
        implementation(libs.netty.handler)
        implementation(libs.netty.codec.http)
        implementation(libs.netty.codec.http2)
        implementation(libs.bouncycastle.bcprov)
        implementation(libs.bouncycastle.bcpkix)
        implementation(libs.commons.lang3)
        implementation(libs.jdom2)
        implementation(libs.jose4j)
        implementation(libs.apache.httpclient)
    }
}
