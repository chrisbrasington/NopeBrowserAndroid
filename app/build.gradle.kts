plugins {
    id("com.android.application")
}

// The whitelist that ships is whatever $NOPE_WHITELIST holds — a comma or space
// separated list of domains. Unset, the checked-in sample in
// res/values/whitelist.xml is used as it stands. That keeps a personal list of
// trusted domains out of a public repo while leaving the file there to explain
// itself.
//
//     NOPE_WHITELIST="example.com,example.org" ./build.sh
//
// Generated into a build-type resource directory rather than into main, because
// build-type resources override main — two files declaring the same string-array
// inside main would be a duplicate-resource error instead.
val whitelistOverride: String? = System.getenv("NOPE_WHITELIST")
val whitelistResDir = layout.buildDirectory.dir("generated/res/whitelist").get().asFile
val whitelistFile = File(whitelistResDir, "values/whitelist.xml")

// Written while the build is being configured, on purpose: the file has to exist
// before AGP wires up resource merging, and it is seven lines of XML.
whitelistFile.parentFile.mkdirs()
if (whitelistOverride.isNullOrBlank()) {
    // Nothing to override with. Delete whatever a previous run with the variable
    // set left behind, or it would quietly keep winning over the sample.
    whitelistFile.delete()
} else {
    val domains = whitelistOverride.split(",", " ", "\n", "\t")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val malformed = domains.filterNot { it.matches(Regex("[A-Za-z0-9.-]+")) }
    require(malformed.isEmpty()) {
        "NOPE_WHITELIST wants bare domains, no scheme and no path. Cannot use: $malformed"
    }
    whitelistFile.writeText(buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<!-- Generated from \$NOPE_WHITELIST. Edits here are overwritten. -->")
        appendLine("<resources>")
        appendLine("""    <string-array name="whitelisted_domains">""")
        domains.forEach { appendLine("        <item>$it</item>") }
        appendLine("    </string-array>")
        appendLine("</resources>")
    })
    logger.lifecycle("Whitelist from \$NOPE_WHITELIST: ${domains.joinToString(", ")}")
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
        versionCode = 3
        versionName = "1.2"
    }

    // Empty unless $NOPE_WHITELIST was set above, in which case it holds the
    // whitelist that overrides res/values/whitelist.xml.
    sourceSets {
        getByName("debug") { res.srcDir(whitelistResDir) }
        getByName("release") { res.srcDir(whitelistResDir) }
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
