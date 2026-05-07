import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

// API key de Google TTS se inyecta vía propiedad de Gradle (definida en gradle.properties)
// Ejemplo en gradle.properties (NO subir a git):
// GOOGLE_TTS_API_KEY=tu_api_key_de_google_tts
val googleTtsApiKey: String = (project.findProperty("GOOGLE_TTS_API_KEY") as? String).orEmpty()

// Configuración Supabase: se lee desde local.properties para no hardcodear claves
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}
val rawSupabaseUrl: String = localProps.getProperty("SUPABASE_URL", "").trim()
val supabaseUrl: String = rawSupabaseUrl.substringBefore("#").trim()
val rawSupabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY", "").trim()
val supabaseAnonKey: String = rawSupabaseAnonKey.substringBefore("#").trim()
val rawTemiEdgeBaseUrl: String = localProps.getProperty("TEMI_EDGE_BASE_URL", "").trim()
val temiEdgeBaseUrl: String = rawTemiEdgeBaseUrl.substringBefore("#").trim()
val tourRecepcionId: String = localProps.getProperty("TOUR_RECEPCION_ID", "").trim()
val telegramBotToken: String = localProps.getProperty("TELEGRAM_BOT_TOKEN", "").trim()
val telegramChatId: String = localProps.getProperty("TELEGRAM_CHAT_ID", "").trim()

// Mover el directorio de build del mГіdulo fuera de OneDrive para evitar bloqueos en Windows
buildDir = File(System.getProperty("user.home"), "TemiDeamonDBBuild/app")

android {
    namespace = "com.spatium.deamon.db.temi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spatium.deamon.db.temi"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "3.7"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exponer la API key de TTS a BuildConfig para usarla en el código Kotlin
        buildConfigField("String", "GOOGLE_TTS_API_KEY", "\"$googleTtsApiKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "TEMI_EDGE_BASE_URL", "\"$temiEdgeBaseUrl\"")
        buildConfigField("String", "TOUR_RECEPCION_ID", "\"$tourRecepcionId\"")
        // Feature flag para activar/desactivar el worker de Supabase en runtime
        buildConfigField("boolean", "ENABLE_SUPABASE_WORKER", "true")
        buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"$telegramBotToken\"")
        buildConfigField("String", "TELEGRAM_CHAT_ID", "\"$telegramChatId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "USE_FAKE_ROBOT", "false")
        }
        debug {
            isMinifyEnabled = false
            enableUnitTestCoverage = true
            buildConfigField("boolean", "USE_FAKE_ROBOT", "false")
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
        buildConfig = true
    }
    lint {
        disable += "MissingClass"
        abortOnError = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Temi SDK disponible también en debug para pruebas en robot.
    // Actualizado a 1.136.0
    releaseImplementation("com.robotemi:sdk:1.136.0")
    debugImplementation("com.robotemi:sdk:1.136.0")

    // QR scanning (ML Kit + CameraX)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Image loading for background
    implementation("io.coil-kt:coil:2.6.0")

    // HTTP client for webhooks
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines for background work (webhook posting, timers)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Supabase-kt (Postgrest + Realtime) y Ktor client para Android
    implementation(platform("io.github.jan-tennert.supabase:bom:2.4.2"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")

    // Lottie para animaciones
    implementation("com.airbnb.android:lottie:6.4.0")

    // Test dependencies
    testImplementation("org.json:json:20231013") // real org.json — overrides Android stub so JSONObject works in JVM tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required = true
        html.required = true
    }

    val excludes = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
    )

    classDirectories.setFrom(
        fileTree("$buildDir/tmp/kotlin-classes/debug") { exclude(excludes) },
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(buildDir) {
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
}
