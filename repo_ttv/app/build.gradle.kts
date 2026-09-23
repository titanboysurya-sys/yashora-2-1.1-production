import java.net.URL
import java.net.URI
import java.net.HttpURLConnection
import java.io.File
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.ritvyom.yashoraReelgenerator"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  // Restores debug.keystore from debug.keystore.base64 if missing locally in Android Studio
  val debugKeystoreFile = file("${rootDir}/debug.keystore")
  val base64File = file("${rootDir}/debug.keystore.base64")
  if (!debugKeystoreFile.exists() && base64File.exists()) {
    try {
      val base64Text = base64File.readText().replace("\\s".toRegex(), "")
      val decodedBytes = Base64.getDecoder().decode(base64Text)
      debugKeystoreFile.writeBytes(decodedBytes)
      println("Decoded debug.keystore from debug.keystore.base64 successfully.")
    } catch (e: Exception) {
      println("Failed to automatically restore debug.keystore: ${e.message}")
    }
  }

  defaultConfig {
    applicationId = "com.ritvyom.yashoraReelgenerator"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      if (debugKeystoreFile.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      } else {
        println("debug.keystore not found. Falling back to default built-in debug signing.")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck)
  implementation(libs.firebase.appcheck.debug)
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation("com.google.zxing:core:3.5.3")
  implementation(libs.coil.compose)
  implementation(libs.google.play.services.ads)
  implementation(libs.play.services.auth)
  implementation("com.google.ads.mediation:applovin:13.0.1.1")
  implementation("com.google.ads.mediation:unity:4.12.5.0")
  implementation("com.google.ads.mediation:facebook:6.18.0.0")
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation("androidx.media3:media3-transformer:1.3.1")
  implementation("androidx.media3:media3-effect:1.3.1")
  implementation("androidx.media3:media3-common:1.3.1")
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

val downloadDefaultVideoTask = tasks.register("downloadDefaultVideo") {
  doLast {
    val rawDir = file("src/main/res/raw")
    if (!rawDir.exists()) {
      rawDir.mkdirs()
    }
    val targetFile = file("src/main/res/raw/yashora_default_video.mp4")
    if (!targetFile.exists()) {
      println("Downloading original vertical 9:16 fallback video...")
      try {
        val url = URL("https://res.cloudinary.com/demo/video/upload/c_fill,g_auto,h_640,w_360/desert.mp4")
        val bytes = url.readBytes()
        targetFile.writeBytes(bytes)
        println("Original vertical fallback video downloaded successfully! Size: ${bytes.size} bytes")
      } catch (e: Exception) {
        println("Primary vertical download failed (${e.message}), trying alternative fallback...")
        try {
          val url = URL("https://www.w3schools.com/html/mov_bbb.mp4")
          val bytes = url.readBytes()
          targetFile.writeBytes(bytes)
          println("Alternative raw fallback downloaded successfully! Size: ${bytes.size} bytes")
        } catch (ex: Exception) {
          println("Failed cascading download: ${ex.message}. Creating standard media placeholder.")
          targetFile.writeBytes(ByteArray(1024))
        }
      }
    }
  }
}

tasks.named("preBuild") {
  dependsOn(downloadDefaultVideoTask)
}

