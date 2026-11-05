plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.mathgate"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.mathgate"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "0.9.1"
    }
    signingConfigs {
        create("stableRelease") {
            storeFile = file(System.getenv("MATHGATE_KEYSTORE_PATH") ?: "MISSING_RELEASE_KEYSTORE")
            storePassword = System.getenv("MATHGATE_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("MATHGATE_KEY_ALIAS") ?: "mathgate"
            keyPassword = System.getenv("MATHGATE_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("stableRelease")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
