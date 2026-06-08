// app/build.gradle.kts -
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dagger.hilt.android)
    id("kotlin-kapt")
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

        // শুধুমাত্র ARM64-v8a এর জন্য বিল্ড
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
