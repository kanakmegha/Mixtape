import java.net.URI
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt.android.plugin)
  id("kotlin-kapt")
}

// Release signing material lives OUTSIDE version control.
// Provide android/keystore.properties (gitignored) or MIXTAPE_* environment variables.
// See docs/RELEASE.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "MIXTAPE_KEYSTORE_PATH")

val stagingBaseUrlProp = (project.findProperty("mixtapeStagingBaseUrl") as String?)
    ?: System.getenv("MIXTAPE_STAGING_BASE_URL")
val isStagingConfigured = !stagingBaseUrlProp.isNullOrBlank()
val stagingUrl = if (isStagingConfigured) {
    if (stagingBaseUrlProp!!.endsWith("/")) stagingBaseUrlProp else "$stagingBaseUrlProp/"
} else {
    "https://staging-unconfigured.mixtape.internal/api/"
}

// Production API URL handling: defaults to official production backend on Vercel.
// Can be overridden via Gradle property -PmixtapeApiBaseUrl=<url> or env MIXTAPE_API_BASE_URL
// for safe local/staging testing without touching production data or deploying premature migrations.
val defaultProductionApiUrl = "https://backend-woad-xi-54.vercel.app/api/"
val rawApiBaseUrl = (project.findProperty("mixtapeApiBaseUrl") as String?)
    ?: System.getenv("MIXTAPE_API_BASE_URL")

val isApiBaseUrlOverridden = project.hasProperty("mixtapeApiBaseUrl") || System.getenv("MIXTAPE_API_BASE_URL") != null

val productionApiUrl: String = if (isApiBaseUrlOverridden) {
    val trimmed = rawApiBaseUrl?.trim() ?: ""
    if (trimmed.isEmpty()) {
        throw GradleException(
            "Gradle property 'mixtapeApiBaseUrl' was specified but is empty. " +
            "Please provide a valid test backend URL (e.g. http://192.168.1.83:8081/api/) or omit the property to use production."
        )
    }
    try {
        val uri = URI(trimmed)
        if (uri.scheme != "http" && uri.scheme != "https") {
            throw GradleException(
                "Property 'mixtapeApiBaseUrl' must start with http:// or https:// (received: '$trimmed')"
            )
        }
        if (uri.host.isNullOrBlank()) {
            throw GradleException(
                "Property 'mixtapeApiBaseUrl' must contain a valid host name or IP address (received: '$trimmed')"
            )
        }
    } catch (e: Exception) {
        if (e is GradleException) throw e
        throw GradleException("Property 'mixtapeApiBaseUrl' is not a valid URL: '$trimmed'. Cause: ${e.message}")
    }
    if (trimmed.endsWith("/")) trimmed else "$trimmed/"
} else {
    defaultProductionApiUrl
}

android {
    namespace = "com.example.privatemusiclibrary"
    compileSdk = 36

    defaultConfig {
      applicationId = "com.example.privatemusiclibrary"
      minSdk = 24
      targetSdk = 36

      // Include only 64-bit ARM libraries to reduce APK size.
      ndk {
        abiFilters += listOf("arm64-v8a")
      }

      manifestPlaceholders["usesCleartextTraffic"] = "false"

      // Bump versionCode for EVERY distributed release (must strictly increase).
      versionCode = (project.findProperty("mixtape.versionCode") as String?)?.toInt() ?: 10005
      versionName = (project.findProperty("mixtape.versionName") as String?) ?: "1.0.6"
    }

    flavorDimensions += listOf("environment")
    productFlavors {
        create("production") {
            dimension = "environment"
            // Production keeps the base applicationId "com.example.privatemusiclibrary"
            buildConfigField("String", "AUDIO_STORAGE_DIRECTORY", "\"Mixtape\"")
            buildConfigField("String", "AUDIO_RELATIVE_PATH", "\"Music/Mixtape/\"")
            buildConfigField("Boolean", "IS_BACKEND_CONFIGURED", "true")
            buildConfigField("String", "STAGING_API_BASE_URL", "\"\"")
        }

        create("internal") {
            dimension = "environment"
            applicationId = "com.example.privatemusiclibrary.internal"
            buildConfigField("String", "AUDIO_STORAGE_DIRECTORY", "\"Mixtape-Test\"")
            buildConfigField("String", "AUDIO_RELATIVE_PATH", "\"Music/Mixtape-Test/\"")
            buildConfigField("Boolean", "IS_BACKEND_CONFIGURED", "$isStagingConfigured")
            buildConfigField("String", "STAGING_API_BASE_URL", "\"$stagingUrl\"")
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "MIXTAPE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "MIXTAPE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "MIXTAPE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.83:8081/api/\"")
        }

        release {
          isMinifyEnabled = true
          isDebuggable = false
          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
          // Unsigned if no keystore is configured; never falls back to the debug key.
          signingConfig = signingConfigs.findByName("release")
          // Production API URL: uses official production URL when mixtapeApiBaseUrl is omitted,
          // or the validated test backend URL when explicitly supplied.
          buildConfigField("String", "API_BASE_URL", "\"$productionApiUrl\"")
          manifestPlaceholders["usesCleartextTraffic"] = if (productionApiUrl.startsWith("http://")) "true" else "false"
      }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    testOptions {
      unitTests {
        isReturnDefaultValues = true
      }
    }

    packaging {
      jniLibs {
        useLegacyPackaging = true
      }
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}


dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner, SQLite JDBC
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.androidx.navigation.compose)
  implementation("androidx.documentfile:documentfile:1.0.1")

  // Room
  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  // Hilt
  implementation(libs.hilt.android)
  "kapt"(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  // Media3
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.media3.ui)
  implementation(libs.media3.common)

  // WorkManager
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation("androidx.hilt:hilt-work:1.2.0")
  ksp("androidx.hilt:hilt-compiler:1.2.0")

  // YoutubeDL (Media extraction)
  implementation(libs.youtubedl.android)
  implementation(libs.youtubedl.ffmpeg)

  // Network & Auth
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-gson:2.11.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
  // XZ compression support required by Apache Commons Compress
  implementation("org.tukaani:xz:1.10")
}

// Preserve backwards-compatible release APK path expected by CI and docs (app/build/outputs/apk/release/app-release.apk)
tasks.register<Copy>("copyLegacyReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/production/release"))
    into(layout.buildDirectory.dir("outputs/apk/release"))
    include("app-production-release.apk")
    rename { "app-release.apk" }
}

tasks.register<Copy>("copyLegacyReleaseUnsignedApk") {
    from(layout.buildDirectory.dir("outputs/apk/production/release"))
    into(layout.buildDirectory.dir("outputs/apk/release"))
    include("app-production-release-unsigned.apk")
    rename { "app-release-unsigned.apk" }
}

tasks.matching { it.name == "assembleProductionRelease" || it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyLegacyReleaseApk", "copyLegacyReleaseUnsignedApk")
}

// Explicit testDebugUnitTest task to preserve backwards compatibility with CI and scripts
tasks.register("testDebugUnitTest") {
    description = "Runs unit tests for all debug variants (productionDebug and internalDebug)."
    group = "verification"
    dependsOn("testProductionDebugUnitTest", "testInternalDebugUnitTest")
}

tasks.register("testReleaseUnitTest") {
    description = "Runs unit tests for release variants or delegates to testDebugUnitTest."
    group = "verification"
    dependsOn("testDebugUnitTest")
}
