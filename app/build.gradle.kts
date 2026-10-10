import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val sherpaVersion = "1.13.8"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx.aar").asFile

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
        versionCode = 53
        versionName = "1.28.0"
        // The on-device speech recognition library only ships 64-bit ARM code.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Every release must be signed with the same key, otherwise Android refuses to install an update
    // over the installed app ("App not installed"). CI may pass its own keystore through
    // ENGLISH_STUDY_KEYSTORE_PATH (a repository secret); otherwise the keystore committed under
    // signing/ is used, so releases built by anyone stay update-compatible.
    val keystore = System.getenv("ENGLISH_STUDY_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { file(it) }
        ?: rootProject.file("signing/english-study.keystore").takeIf { it.exists() }
    if (keystore != null) {
        signingConfigs.getByName("debug") {
            storeFile = keystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures { compose = true }

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
