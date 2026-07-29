plugins {
    id("com.android.application")
}

android {
    namespace = "com.chrisincode.NopeBrowser"
    compileSdk = 35

    // Pinned to what the build image already contains. Left unset, AGP 8.7 asks for
    // build-tools 34.0.0 and re-downloads it on every `podman run --rm`.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.chrisincode.render"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Stable, checked-in signing key so every rebuild (on any machine) signs
    // identically — otherwise `adb install -r` fails with a signature mismatch. The
    // build runs in a container with no persistent ~/.android/debug.keystore, so the
    // default debug key would differ every run. build.sh generates this on first use.
    signingConfigs {
        create("render") {
            storeFile = file("render.keystore")
            storePassword = "renderpass"
            keyAlias = "render"
            keyPassword = "renderpass"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("render")
        }
        release {
            signingConfig = signingConfigs.getByName("render")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// No dependencies. Not AndroidX, not Material, nothing. The whole app is the
// framework WebView plus one Activity, and every added library is more code with
// network access inside a process whose entire job is loading hostile pages.
