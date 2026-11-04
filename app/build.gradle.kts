plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "no.cloud247.tvlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.cloud247.tvlauncher"
        // Android 5.0+ dekker alle Philips Android TV-modeller.
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Signering: sett GitHub Secrets (se README) for en fast nøkkel slik at nye versjoner
    // kan installeres over gamle. Uten nøkkel signeres APK-en med debug-nøkkelen.
    val keystorePath = System.getenv("LAUNCHER_KEYSTORE")
    val hasReleaseKey = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("LAUNCHER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LAUNCHER_KEY_ALIAS")
                keyPassword = System.getenv("LAUNCHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
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
        buildConfig = false
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // Bevisst minimalt: ingen AppCompat, ingen Compose, ingen Leanback-bibliotek.
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
