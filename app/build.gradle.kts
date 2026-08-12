import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing: loaded from rootProject/keystore.properties if present.
// Falls back to an unsigned release build when absent (fresh clone / CI without secrets).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("io.ktor:ktor-client-core:3.5.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:3.5.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val androidMain by getting {
            kotlin.srcDir("src/main/java")
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("androidx.compose.ui:ui-tooling-preview")
                implementation("androidx.compose.material:material-icons-extended")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("io.ktor:ktor-client-okhttp:3.5.2")
                implementation("androidx.media3:media3-exoplayer:1.4.1")
                implementation("androidx.media3:media3-ui:1.4.1")
                implementation("io.coil-kt:coil-compose:2.6.0")
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
            }
        }
        val wasmJsMain by getting {
            // lc-debt: compose.html.core dropped — org.jetbrains.compose.html only publishes a
            // Kotlin/JS variant, not Kotlin/Wasm (resolution fails: no wasmJs variant of
            // html-core). Upgrade path: if Compose HTML ever ships a wasmJs variant and
            // DOM-based rendering is wanted instead of canvas, re-add it here.
            dependencies {
                // CanvasBasedWindow (main.kt) needs compose.ui explicitly — not transitively
                // visible from compose.foundation/material3 on the wasmJs compile classpath.
                implementation(compose.ui)
                implementation("io.ktor:ktor-client-js:3.5.2")
            }
        }
    }
}

android {
    namespace = "com.personal.twelveweek"
    compileSdk = 36

    // AGP defaults the "main" Android source set to src/androidMain/* once the KMP
    // androidTarget is applied. Point it back at the existing src/main/* layout so no
    // existing Android source files (manifest, res, assets) need to move.
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
        }
    }

    defaultConfig {
        applicationId = "com.personal.twelveweek"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    add("androidMainImplementation", composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.json:json:20240303")
}
