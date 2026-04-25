import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

// Move keystore loading outside to avoid scope issues in KTS
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Load local.properties for bootstrap tokens
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.beemovil"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beemovil.emma"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "7.0.0"  // Voice Intelligence + LifeStream

        // Multidex for 65K+ method count
        multiDexEnabled = true

        // Bootstrap HF token for first-time Gemma downloads (from local.properties, gitignored)
        buildConfigField("String", "HF_BOOTSTRAP_TOKEN",
            "\"${localProperties.getProperty("hf.bootstrap.token", "")}\"")
    }

    // Sprint 7: Signing config
    signingConfigs {
        val storeFilePath = keystoreProperties.getProperty("storeFile", "")
        if (storeFilePath.isNotBlank() && rootProject.file(storeFilePath).exists()) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword", "")
                keyAlias = keystoreProperties.getProperty("keyAlias", "")
                keyPassword = keystoreProperties.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            // NOTE: NO applicationIdSuffix here — it breaks Google OAuth
            // because Cloud Console only has com.beemovil.emma registered
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Prevent lint from blocking release builds
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // LiteRT-LM 0.9+ is compiled with Kotlin 2.2, allow cross-version usage
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/mailcap",
                "META-INF/mimetypes.default",
                "META-INF/DEPENDENCIES",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/**",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "mozilla/public-suffix-list.txt"
            )
        }
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Security (EncryptedSharedPreferences for API keys/tokens)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // HTTP (for OpenRouter API calls)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // On-device LLM inference (Gemma 4 via LiteRT-LM — replaces deprecated MediaPipe GenAI)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.9.0")

    // JavaScript Engine (Coding Sandbox)
    implementation("org.mozilla:rhino:1.7.14")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")

    // Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")

    // Email (IMAP/SMTP) — Android-compatible JavaMail
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // CameraX (Live Vision mode)
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Face Detection, OCR & Barcode Scanning
    implementation("com.google.mlkit:face-detection:16.1.6")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Document Reader (PDF text extraction + DOCX/XLSX parsing)
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        // Exclude unused XML schema validation to reduce APK size
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // HTML Parser (Web Scraper)
    implementation("org.jsoup:jsoup:1.17.2")



    // Google Sign-In (Credential Manager) + Workspace APIs
    implementation("androidx.credentials:credentials:1.5.0-alpha05")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0-alpha05")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Google Identity Services (AuthorizationClient for OAuth2 scope requests)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Google Drive API v3
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20250220-2.0.0")

    // Google Calendar API v3
    implementation("com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0")

    // Google Tasks API v1 (Sprint 4)
    implementation("com.google.apis:google-api-services-tasks:v1-rev20250302-2.0.0")

    // Google Gmail API v1 (Sprint 4)
    implementation("com.google.apis:google-api-services-gmail:v1-rev20260112-2.0.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Location (Real GPS for Authentic Telemetry, Fase 11)
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Lottie Compose (AAA Animations)
    implementation("com.airbnb.android:lottie-compose:6.0.0")

    // Coil Compose (Dynamic Photo Widgets)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Google Auth (OAuth2 token handling)
    implementation("com.google.auth:google-auth-library-oauth2-http:1.32.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
}
