plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-2.0.1.aar")
val downloadSherpaAar by tasks.registering {
    outputs.file(sherpaAar)
    doLast {
        val target = sherpaAar.asFile
        if (!target.isFile || target.length() < 10_000_000L) {
            target.parentFile.mkdirs()
            val partial = File(target.parentFile, target.name + ".part")
            val connection = java.net.URL(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar"
            ).openConnection().apply {
                connectTimeout = 20000
                readTimeout = 120000
            }
            connection.getInputStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            check(partial.length() > 10_000_000L) { "Downloaded Sherpa ONNX AAR is incomplete" }
            target.delete()
            check(partial.renameTo(target)) { "Could not finalize Sherpa ONNX AAR" }
        }
    }
}

android {
    namespace = "com.itsmuzz.peakcut"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.itsmuzz.peakcut"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
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
    implementation(files(sherpaAar))
    implementation("org.apache.commons:commons-compress:1.27.1")
}

tasks.named("preBuild").configure {
    dependsOn(downloadSherpaAar)
}
