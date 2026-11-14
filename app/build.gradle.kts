import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from /keystore.properties (gitignored). See README.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Pleos Playground project credentials from /.env (gitignored). See .env.example.
val envFile: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().removeSurrounding("\"") }
    .orEmpty()

fun pleosEnv(key: String) = System.getenv(key) ?: envFile[key].orEmpty()

android {
    namespace = "com.hmgchallenge.vehicleapp"
    compileSdk = 36

    defaultConfig {
        // Must match the package registered in the Pleos Playground console.
        applicationId = "com.hmgchallenge.vehicleapp"
        // Pleos requires API 28+ (APK signing key rotation / lineage).
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // TextToSpeech / SpeechToText app registration.
        buildConfigField("String", "PLEOS_PROJECT_ID", "\"${pleosEnv("PROJECT_ID")}\"")
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${pleosEnv("CLIENT_ID")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${pleosEnv("CLIENT_SECRET")}\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // Pleos relies on v3 signing (key rotation / lineage); that's why minSdk is 28.
                enableV3Signing = true
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
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.bundles.pleos)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}

// ./gradlew exportContestApk → apk/<team>_<app>_release.apk, the path the contest requires.
tasks.register<Copy>("exportContestApk") {
    dependsOn("assembleRelease")
    val team = providers.gradleProperty("contest.teamName").get()
    val app = providers.gradleProperty("contest.appName").get()
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*-release.apk")
        rename { "${team}_${app}_release.apk" }
    }
    into(rootProject.layout.projectDirectory.dir("apk"))
    doFirst {
        check(rootProject.file("keystore.properties").exists()) {
            "Release APK must be signed: run scripts/create-release-keystore.sh first (see README)."
        }
    }
}
