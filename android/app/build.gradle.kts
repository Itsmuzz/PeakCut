plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.itsmuzz.peakcut"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.itsmuzz.peakcut"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.3")
    implementation("org.apache.commons:commons-compress:1.27.1")
}
