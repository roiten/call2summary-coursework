plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.gigastt.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.gigastt.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        // Huawei Pura 80 = arm64. Native libs are built for arm64-v8a only.
        ndk { abiFilters += "arm64-v8a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // UniFFI Kotlin bindings call libgigastt_uniffi.so through JNA
    // (same versions as gigastt's packaging/android).
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
