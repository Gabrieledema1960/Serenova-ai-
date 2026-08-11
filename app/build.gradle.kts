plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aiphoneassistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.aiphoneassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }
}
