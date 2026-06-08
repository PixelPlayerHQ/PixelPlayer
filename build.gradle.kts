// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    id("kotlin-kapt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.theveloper.pixelplay"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.theveloper.pixelplay"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Restrict to arm64-v8a for faster, smaller release builds
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Basic essentials
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM and UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    
    // Dagger Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
