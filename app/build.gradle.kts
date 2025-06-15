plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.example.virtualkeyboard"
    compileSdk = 35 // Use the latest compileSdk

    defaultConfig {
        applicationId = "com.example.virtualkeyboard"
        minSdk = 24
        targetSdk = 35 // Use the latest targetSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_11 // Update to Java 11 for better performance and modern features
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11" // Update to Kotlin 1.8 and Java 11 for consistency and performance
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // Keep latest stable Compose compiler extension
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}" // Prevent including unnecessary license files
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00") // Latest Compose BOM

    // Core Android dependencies
    implementation(libs.androidx.core.ktx.v1120) // Latest KTX
    implementation(libs.androidx.lifecycle.runtime.ktx.v270) // Latest lifecycle runtime
    implementation(libs.androidx.activity.compose.v182) // Latest Activity Compose

    // Compose dependencies
    implementation(libs.ui) // Core UI library
    implementation(libs.ui.graphics) // Graphics library
    implementation(libs.ui.tooling.preview) // Tooling for previews
    implementation(libs.material3) // Material 3 components
    implementation(libs.androidx.navigation.compose) // Latest Compose Navigation
    implementation("androidx.compose.material3:material3:1.2.1") // Use the latest stable version

    // WebSocket client
    implementation(libs.java.websocket) // WebSocket library for communication

    // QR Code scanning
    implementation(libs.zxing.android.embedded) // ZXing for QR code scanning

    // Testing dependencies
    testImplementation(libs.junit) // JUnit for unit testing
    androidTestImplementation(libs.androidx.junit.v121) // JUnit extensions for Android
    androidTestImplementation(libs.androidx.espresso.core.v361) // Espresso for UI tests
    androidTestImplementation(composeBom) // Use Compose BOM for consistency in testing
    androidTestImplementation(libs.ui.test.junit4) // Compose test libraries
    debugImplementation(libs.ui.tooling) // Tooling for debugging and inspecting Composables
    debugImplementation(libs.ui.test.manifest) // Debugging and test manifest support
}
