plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

android {
    namespace = "com.beemovil"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beemovil.emma"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "5.7.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
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

    // Koog Framework (AI Agents KMP)
    implementation("ai.koog:koog-agents:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

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

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.6")

    // Document Reader (PDF text extraction + DOCX/XLSX parsing)
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        // Exclude unused XML schema validation to reduce APK size
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")



    // Google Sign-In (Credential Manager) + Workspace APIs
    implementation("androidx.credentials:credentials:1.5.0-alpha05")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0-alpha05")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Drive API v3
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20250220-2.0.0")

    // Google Calendar API v3
    implementation("com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google Auth (OAuth2 token handling)
    implementation("com.google.auth:google-auth-library-oauth2-http:1.32.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
}
