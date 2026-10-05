plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shina.dualspace"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shina.dualspace"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "2.4"
    }

    // Stable signing since v2.4: previously every GitHub Actions debug build
    // got a fresh runner debug key, so updates could not install over each
    // other and the two profile copies never matched signatures. This is a
    // throwaway debug-grade keystore committed on purpose (not a release
    // secret; this app is not shipped to Play with it).
    signingConfigs {
        create("shinaStable") {
            storeFile = file("shina-debug.p12")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shinaStable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shinaStable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
