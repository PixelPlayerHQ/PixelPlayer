plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt.android)
    kotlin("plugin.serialization") version "2.1.0"
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.baselineprofile)
    // id("com.google.protobuf") version "0.9.5" // Eliminado plugin de Protobuf
    id("kotlin-parcelize")
}

android {
    namespace = "com.theveloper.pixelplay"
    compileSdk = 35

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    androidResources {
        noCompress.add("tflite")
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "/META-INF/io.netty.versions.properties"
            pickFirsts.add("META-INF/LICENSE.md")
            pickFirsts.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/CONTRIBUTORS.md")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/NOTICE.md")
        }
    }

    defaultConfig {
        applicationId = "com.theveloper.pixelplay"
        minSdk = 29
        targetSdk = 35
        versionCode = (project.findProperty("APP_VERSION_CODE") as String).toInt()
        versionName = project.findProperty("APP_VERSION_NAME") as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        // AGREGA ESTE BLOQUE:
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false // Esto quita el error que mencionaste
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.0"
        // Para habilitar informes de composición (legibles):
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
        // Aquí es donde debes agregar freeCompilerArgs para los informes del compilador de Compose.
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.layout.buildDirectory.get().asFile.absolutePath}/compose_compiler_reports"
        )
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.layout.buildDirectory.get().asFile.absolutePath}/compose_compiler_metrics"
        )

        //Stability
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${project.rootDir.absolutePath}/app/compose_stability.conf"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    // ABI Splits: 为每个 CPU 架构生成独立 APK，避免单 APK 同时打包所有架构 native 库
    // TDLib 单架构约 15~25MB，四架构合计 87MB，开启后每个 APK 只含对应架构
    splits {
        abi {
            isEnable = true
            reset()
            // 覆盖市面上 >99% 设备：arm64-v8a（现代手机）+ armeabi-v7a（旧设备）
            // x86 / x86_64 仅模拟器使用，发布阶段可不包含
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true // 同时生成通用包（保留调试用途）
        }
    }

    // AAB bundle 配置：通过 Google Play 分发时自动按架构拆分（推荐）
    bundle {
        abi {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        language {
            enableSplit = true
        }
    }
}

composeCompiler {
    // Applies Compose's strong skipping optimization (skip composables whose parameters
    // haven't changed) in Debug builds as well, making dev-mode performance more
    // representative of Release and reducing unnecessary recompositions during development.
    enableStrongSkippingMode = true

    // Reduces generated code for non-skippable composables, improving runtime
    // performance by eliminating unnecessary group bookkeeping.
    featureFlags = setOf(
        org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.OptimizeNonSkippingGroups
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.paging.common)
    "baselineProfile"(project(":baselineprofile"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.lifecycle:lifecycle-process:2.9.0")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.generativeai)
    implementation(libs.androidx.navigation.runtime.ktx)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.mockk)
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Keep debug-only Compose tooling on the same version line as the runtime stack.
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Baseline Profiles (Macrobenchmark)
    // Asegúrate de que libs.versions.toml tiene androidxBenchmarkMacroJunit4 y androidxUiautomator
    // Ejemplo: androidx-benchmark-macro-junit4 = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "benchmarkMacro" }
    // benchmarkMacro = "1.2.4"
    androidTestImplementation(libs.androidx.benchmark.macro.junit4)
    androidTestImplementation(libs.androidx.uiautomator)


    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler) // For Dagger Hilt
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler) // Esta línea es crucial y ahora funcionará

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    
    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Glance
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    //Gson
    implementation(libs.gson)

    //Serialization
    implementation(libs.kotlinx.serialization.json)

    //Work
    implementation(libs.androidx.work.runtime.ktx)

    //Duktape
    // implementation(libs.duktape.android)

    //Smooth corners shape
    implementation(libs.smooth.corner.rect.android.compose)
    implementation(libs.androidx.graphics.shapes)

    //Navigation
    implementation(libs.androidx.navigation.compose)

    //Animations
    implementation(libs.androidx.animation)

    //Coil
    implementation(libs.coil.compose)

    //Capturable
    implementation(libs.capturable) {
        // Capturable brings its own loose Compose graph; keep it on the app's Compose line.
        exclude(group = "androidx.compose.animation")
        exclude(group = "androidx.compose.foundation")
        exclude(group = "androidx.compose.material")
        exclude(group = "androidx.compose.runtime")
        exclude(group = "androidx.compose.ui")
    }

    //Reorderable List/Drag and Drop
    // compose.dnd (mohamedrejeb) 未被使用，已删除
    implementation(libs.reorderables)

    //CodeView
    implementation(libs.codeview)

    //AppCompat
    implementation(libs.androidx.appcompat)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.mediarouter)
    implementation(libs.google.play.services.cast.framework)
    implementation(libs.androidx.media3.exoplayer.ffmpeg)

    // Palette API for color extraction
    implementation(libs.androidx.palette.ktx)

    // For foreground service permission (Android 13+)
    implementation(libs.androidx.core.splashscreen) // No directamente para permiso, pero útil

    //ConstraintLayout
    implementation(libs.androidx.constraintlayout.compose)

    //Foundation
    implementation(libs.androidx.foundation)
    //Wavy slider
    implementation(libs.wavy.slider)

    // Splash Screen API
    implementation(libs.androidx.core.splashscreen) // O la versión más reciente

    //Icons
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Protobuf (JavaLite es suficiente para Android y más pequeño)
    // implementation(libs.protobuf.javalite) // Eliminada dependencia de Protobuf

    //Material library
    implementation(libs.material)

    // Kotlin Collections
    implementation(libs.kotlinx.collections.immutable) // Verifica la última versión

    // Gemini — 使用 com.google.ai.client.generativeai (已在上方声明)
    // google.genai (Java JVM SDK) 未被任何代码引用，已移除

    //permisisons
    implementation(libs.accompanist.permissions)

    //Audio editing
    // Spleeter para separación de audio
    //implementation(libs.tensorflow.lite)
    //implementation(libs.tensorflow.lite.support)
    ///implementation(libs.tensorflow.lite.select.tf.ops)

    // Compose-audiowaveform para la UI

    // Media3 Transformer (ya debería estar, pero asegúrate)
    implementation(libs.androidx.media3.transformer)

    //implementation(libs.pytorch.android)
    //implementation(libs.pytorch.android.torchvision)

    //Checker framework
    implementation(libs.checker.qual)

    // Timber
    implementation(libs.timber)

    // TagLib for metadata editing (supports mp3, flac, m4a, etc.)
    implementation(libs.taglib)
    // JAudioTagger fallback for files where TagLib can't map ID3 frames (e.g. 48kHz ffmpeg encodes)
    implementation(libs.jaudiotagger)
    // VorbisJava for Opus/Ogg metadata editing (TagLib has issues with Opus via file descriptors)
    implementation(libs.vorbisjava.core)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Ktor for HTTP Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.ui.text.google.fonts)

    implementation(libs.accompanist.drawablepainter)
    implementation(kotlin("test"))

    // Android Auto
    implementation(libs.androidx.media)
    implementation(libs.androidx.app)
    implementation(libs.androidx.app.projected)

    // Wear OS Data Layer
    implementation(project(":shared"))
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Telegram TDLib
    implementation(libs.tdlib)

    // Google Sign-In via Credential Manager (for Google Drive)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Kuromoji
    implementation(libs.kuromoji.ipadic)

    // Pinyin
    implementation(libs.pinyin4j.core)

    // Encrypted credentials storage
    implementation(libs.androidx.security.crypto)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
