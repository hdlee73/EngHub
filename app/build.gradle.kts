plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hdlee73.englishstudy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hdlee73.englishstudy"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.9.0"
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

    compileOptions {
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
}
