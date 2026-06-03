plugins {
    id("com.android.library")
    
    alias(libs.plugins.ksp)
    id("com.vanniktech.maven.publish") version "0.34.0"
}

android {
    namespace = "dev.brahmkshatriya.echo.extension.loader"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.kotlinx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.security.crypto)
}

mavenPublishing {
    publishToMavenCentral(true)
    signAllPublications()

    coordinates("dev.brahmkshatriya.echo", "extension-loader", "1.0.0")

    pom {
        name = "Echo extension loader library"
        description = "A standalone library to load and manage Echo compatible extensions."
        inceptionYear = "2026"
        url = "https://github.com/brahmkshatriya/echo"
        licenses {
            license {
                name = "Unabandon Public License"
                url = "https://github.com/brahmkshatriya/echo/blob/main/LICENSE.md"
                distribution = "https://github.com/brahmkshatriya/echo/blob/main/LICENSE.md"
            }
        }
        developers {
            developer {
                id = "brahmkshatriya"
                name = "Shivam"
                url = "https://github.com/brahmkshatriya/"
            }
        }
        scm {
            url = "https://github.com/brahmkshatriya/echo/"
            connection = "scm:git:git://github.com/brahmkshatriya/echo.git"
            developerConnection = "scm:git:ssh://git@github.com/brahmkshatriya/echo.git"
        }
    }
}
