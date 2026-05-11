plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val releaseStoreFile = providers.environmentVariable("FJORA_RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("FJORA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("FJORA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("FJORA_RELEASE_KEY_PASSWORD")
fun org.gradle.api.provider.Provider<String>.hasText(): Boolean =
    orNull?.isNotBlank() == true

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.hasText() }
val hasPartialReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).any { it.hasText() } && !hasReleaseSigning

if (hasPartialReleaseSigning) {
    error(
        "Release signing is partially configured. Set all of " +
            "FJORA_RELEASE_STORE_FILE, FJORA_RELEASE_STORE_PASSWORD, " +
            "FJORA_RELEASE_KEY_ALIAS, and FJORA_RELEASE_KEY_PASSWORD, or unset all of them."
    )
}

android {
    namespace = "com.example.jellyfinplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.fjora.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        // Generates BuildConfig.DEBUG so the repo can disable HTTP logging in
        // release builds (it was leaking api_key= tokens to logcat in 1.0).
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Core / Compose
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    // Material icons (extended set). The BOM doesn't constrain this artifact
    // so we pin a known-good 1.6.x version. We use icons from this set for
    // most of the UI (Movie, Tv, Visibility, Audiotrack, Subtitles, etc.).
    // For the one icon that's missing from this version (Download), we
    // provide a custom inline ImageVector at
    // com.example.jellyfinplayer.ui.icons.DownloadIconVector.
    implementation("androidx.compose.material:material-icons-extended:1.6.1")

    // Media3 / ExoPlayer (background audio + video)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // libmpv - same artifact Findroid uses. Bundles libmpv + ffmpeg + libass
    // as native .so files for arm64/armv7/x86_64. Used as a fallback player
    // for downloaded files that ExoPlayer can't seek (e.g. MKVs without
    // SeekHead/Cues), and as the user's optional default if they prefer
    // mpv's broader format support.
    //
    // ~30 MB APK size impact; the user explicitly opts in via settings, so
    // the trade-off is worth it for compatibility. Maven Central artifact,
    // MIT-licensed wrapper around the GPL-licensed libmpv (see project for
    // licensing nuance: the wrapper itself is MIT, but the .so files
    // include GPL components).
    implementation("dev.jdtech.mpv:libmpv:0.5.1")

    // Networking
    // NOTE: converter-kotlinx-serialization was first published with Retrofit 2.11.0.
    // The previous 2.9.0 pin made the dependency unresolvable -> compile failed.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // DataStore for auth/server persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
