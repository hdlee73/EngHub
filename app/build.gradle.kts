import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val sherpaVersion = "1.13.8"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx.aar").asFile

/** Reads a build secret from a Gradle property first, then from an environment variable. */
fun secret(property: String, env: String): String? =
    (findProperty(property) as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(env)?.trim()?.takeIf { it.isNotEmpty() }

// The Edge text-to-speech client token is a credential: it never lives in the source. Supply it through the
// "edgeTtsToken" Gradle property (e.g. in ~/.gradle/gradle.properties) or the EDGE_TTS_TOKEN environment
// variable. When it is missing the online voice fails with a clear message instead of silently misbehaving.
val edgeTtsToken: String = secret("edgeTtsToken", "EDGE_TTS_TOKEN").orEmpty()

// Release signing. The keystore and its passwords are secrets and must NOT be committed (see .gitignore and the
// README): set them as Gradle properties or environment variables / CI secrets. When they are absent the release
// build is simply left unsigned and the debug build keeps using the standard Android debug keystore, so local
// development is never blocked.
// TODO(security): the keystore that used to be committed as signing/english-study.keystore (alias
// "androiddebugkey", password "android") is public and must be treated as compromised: generate a new release key,
// store it outside the repository, and purge the old file from the git history before shipping again.
val signingKeystorePath = secret("engHubKeystorePath", "ENGLISH_STUDY_KEYSTORE_PATH")
val signingStoreFile: java.io.File? = signingKeystorePath?.let { file(it) }
    ?: rootProject.file("signing/english-study.keystore").takeIf { it.exists() }
val signingStorePassword = secret("engHubStorePassword", "ENGLISH_STUDY_STORE_PASSWORD")
val signingKeyAlias = secret("engHubKeyAlias", "ENGLISH_STUDY_KEY_ALIAS")
val signingKeyPassword = secret("engHubKeyPassword", "ENGLISH_STUDY_KEY_PASSWORD")
val hasReleaseSigning = signingStoreFile != null && signingStorePassword != null &&
    signingKeyAlias != null && signingKeyPassword != null

// The sherpa-onnx Android library (on-device speech recognition and speaker separation for the DocVoice tab) is not kept in the
// repository: it is downloaded when the app is built.
val downloadSherpa by tasks.registering {
    outputs.file(sherpaAar)
    onlyIf { !sherpaAar.exists() || sherpaAar.length() < 1_000_000 }
    doLast {
        sherpaAar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        logger.lifecycle("Downloading $url")
        URI(url).toURL().openStream().use { input ->
            sherpaAar.outputStream().use { input.copyTo(it) }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadSherpa) }
tasks.matching { it.name.endsWith("AarMetadata") || it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(downloadSherpa) }

android {
    namespace = "com.hdlee73.englishstudy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hdlee73.englishstudy"
        minSdk = 29
        targetSdk = 36
        // Keep in step with the Windows port (EngHub_Win) when releasing; RELEASE_NOTES.md lists what each version
        // contains. Do not invent a version here: only bump it together with the matching Windows release.
        versionCode = 70
        versionName = "1.41.0"
        // The on-device speech recognition library (DocVoice) only ships 64-bit ARM code, so its features need an
        // arm64-v8a device; the other ABIs are still built so the rest of the app runs on 32-bit phones and x86_64
        // emulators instead of refusing to install.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // Edge TTS credential exposed as a BuildConfig field; empty when no token was configured at build time.
        buildConfigField("String", "EDGE_TTS_TOKEN", "\"" + edgeTtsToken.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Signed only when real credentials were supplied (see hasReleaseSigning above); otherwise the APK is
            // built unsigned so the build itself never fails for lack of secrets.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        jniLibs.useLegacyPackaging = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(files("libs/sherpa-onnx.aar"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.pdfbox.android)
    implementation(libs.commons.compress)
    implementation(libs.newpipe.extractor)
    implementation(libs.lame)
    // On-device AI: ML Kit translation (offline fallback) and Gemini Nano through ML Kit GenAI (AICore).
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.genai.proofreading)
    implementation(libs.mlkit.genai.rewriting)
    implementation(libs.mlkit.genai.prompt)
    coreLibraryDesugaring(libs.desugar)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
