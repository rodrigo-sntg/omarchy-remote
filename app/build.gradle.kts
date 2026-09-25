import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sandevsystems.omarchyremote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sandevsystems.omarchyremote"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
        // Real phones only (S24, S10e): the OCR library ships ~11 MB of native code per architecture.
        ndk { abiFilters += "arm64-v8a" }
    }
    // Release signing: the key lives outside the repository (~/.config/omarchy-remote-release/
    // signing.properties, or OMARCHY_REMOTE_SIGNING pointing at such a file). Without it the release
    // build is simply unsigned.
    val signingFile = file(System.getenv("OMARCHY_REMOTE_SIGNING")
        ?: "${System.getProperty("user.home")}/.config/omarchy-remote-release/signing.properties")
    val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }
    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // Pairing QR (Omarchy: Phone → Pair phone). Google's scanner UI: no camera permission for the app.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // Copiar texto da tela: on-device OCR (Latin model bundled; nothing leaves the phone).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Real org.json for JVM tests; android.jar only has stubs.
    testImplementation("org.json:json:20240303")
}

// Unit tests check the Portuguese text (the app's original); the language follows the JVM's locale.
tasks.withType<Test>().configureEach {
    jvmArgs("-Duser.language=pt", "-Duser.country=BR")
}
