plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.quietdose"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quietdose"
        minSdk = 29
        targetSdk = 35
        // Monotonic across CI builds so each published APK installs as an *update*
        // (never an uninstall), preserving on-device data/history. Falls back to 1
        // for local builds.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // A committed, fixed debug key so every CI build is signed identically.
        // Without this, CI generates a random debug key per run, the signature
        // changes, and Android refuses to update in place (forcing a data-wiping
        // reinstall). Debug-key passwords are the well-known defaults — not secret.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Data layer
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // On-device brain (LLM path is dormant until a model file is present)
    implementation(libs.mediapipe.tasks.genai)

    // On-device label scanning (camera scan → add item): ML Kit text + barcode
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    // Day-data sources: Health Connect (sleep/steps) read
    implementation(libs.androidx.health.connect)

    // Event triggers: geofencing, activity recognition, Sleep API
    implementation(libs.play.services.location)

    debugImplementation(libs.androidx.ui.tooling)
}
