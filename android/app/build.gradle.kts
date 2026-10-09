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

      // Bump versionCode for EVERY distributed release (must strictly increase).
      versionCode = (project.findProperty("mixtape.versionCode") as String?)?.toInt() ?: 1
      versionName = (project.findProperty("mixtape.versionName") as String?) ?: "1.0.0"
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
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.83:8081/api/\"")
        }

        release {
          isMinifyEnabled = true
          isDebuggable = false
          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
          // Unsigned if no keystore is configured; never falls back to the debug key.
          signingConfig = signingConfigs.findByName("release")
          // Production API (public HTTPS, Vercel). Contains no secrets.
          buildConfigField("String", "API_BASE_URL", "\"https://backend-woad-xi-54.vercel.app/api/\"")
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

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

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
