plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.spatium.temibridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spatium.temibridge"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "USE_FAKE_ROBOT", "false")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "USE_FAKE_ROBOT", "true")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Temi SDK solo en release para evitar auto-inicialización en emulador
    releaseImplementation("com.robotemi:sdk:1.131.4")

    // QR scanning (ZXing Embedded)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")

    // Image loading for background
    implementation("io.coil-kt:coil:2.6.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}