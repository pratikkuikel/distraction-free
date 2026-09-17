plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.distractionfree.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.distractionfree.app"
        minSdk = 26
        targetSdk = 35
        // CI overrides these per release so each build has a strictly higher
        // versionCode than the last — required for Android to treat a new
        // APK as an update-in-place rather than refusing the install, which
        // is what keeps stats/streak/timeback data across upgrades. Local
        // dev builds fall back to fixed values.
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            // Populated from env in CI (see .github/workflows/release.yml); a
            // local `./gradlew assembleRelease` without these set falls back
            // to no explicit signing config below, same as before.
            val storeFile = System.getenv("ANDROID_RELEASE_KEYSTORE_PATH")
            if (storeFile != null) {
                this.storeFile = file(storeFile)
                storePassword = System.getenv("ANDROID_RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("ANDROID_RELEASE_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
