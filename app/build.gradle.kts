plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tajmi3.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tajmi3.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Fixed signing key (from GitHub secrets) so every build has the same SHA-1.
    // Without the secrets it falls back to the normal debug key.
    val ksFile = System.getenv("KEYSTORE_FILE")
    if (ksFile != null && file(ksFile).exists()) {
        signingConfigs {
            create("tajmi3") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = "tajmi3"
                keyPassword = System.getenv("KEYSTORE_PASS")
            }
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug {
            signingConfigs.findByName("tajmi3")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint { abortOnError = false }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
