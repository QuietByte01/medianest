import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

val ffmpegExtractionDir = layout.buildDirectory.dir("ffmpeg-kit-extracted")

val extractFFmpegNativeLibs = tasks.register<Copy>("extractFFmpegNativeLibs") {
  val ffmpegConfig = configurations.detachedConfiguration(dependencies.create(libs.ffmpeg.kit.get()))
  ffmpegConfig.isTransitive = false
  val ffmpegAar = ffmpegConfig.singleFile
  from(zipTree(ffmpegAar))
  into(ffmpegExtractionDir)
  include("jni/**/*.so")
  exclude("**/libc++_shared.so")
  eachFile {
    path = path.replaceFirst("jni/", "")
  }
  includeEmptyDirs = false
}

android {
  namespace = "com.medianest"
  compileSdk = 37
  ndkVersion = "29.0.14206865"

  defaultConfig {
    applicationId = "com.medianest.app"
    minSdk = 30
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    
    externalNativeBuild {
      cmake {
        arguments("-DANDROID_STL=c++_shared", "-DFFMPEG_EXTRACTION_DIR=${ffmpegExtractionDir.get().asFile.absolutePath}")
      }
    }
  }

  signingConfigs {
    create("release") {
      val envFile = rootProject.file(".env")
      val envProperties = Properties()
      if (envFile.exists()) {
        val stream = envFile.inputStream()
        envProperties.load(stream)
        stream.close()
      }

      val keystorePath = System.getenv("KEYSTORE_PATH") ?: envProperties.getProperty("KEYSTORE_PATH") ?: "my-upload-key.jks"
      storeFile = rootProject.file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: envProperties.getProperty("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS") ?: envProperties.getProperty("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: envProperties.getProperty("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  /*
  kotlinOptions {
    jvmTarget = "17"
  }
  */
  buildFeatures {
    compose = true
    buildConfig = true
    prefab = true
  }

  composeCompiler {
    // Stability config: lists classes the Compose compiler should treat as @Stable.
    // Without this, data classes with List<T> fields are unstable — child composables
    // receiving them can never be skipped even if nothing they read has changed.
    stabilityConfigurationFiles.add(
      rootProject.layout.projectDirectory.file("compose_stability.conf")
    )
  }

  packaging {
    jniLibs {
      useLegacyPackaging = false
      pickFirsts.add("**/libc++_shared.so")
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  testOptions { 
    unitTests { 
      isIncludeAndroidResources = true 
      all {
        it.maxHeapSize = "4g"
        it.maxParallelForks = 1
      }
    } 
  }

  // ABI splits: reduces APK size from ~90MB to ~25MB for release per-ABI split
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = true // keeps a universal APK as fallback
    }
  }
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
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.documentfile)
  implementation("com.google.oboe:oboe:1.9.3")
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.paging.runtime)
  implementation(libs.androidx.paging.compose)
  implementation(libs.androidx.exifinterface)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation("io.coil-kt:coil-gif:2.7.0")
  implementation("io.coil-kt:coil-svg:2.7.0")

  implementation(libs.media3.exoplayer)
    implementation("androidx.media3:media3-effect:1.11.0")
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.media3.common)
  implementation("androidx.media:media:1.7.0")
  implementation(libs.ffmpeg.kit)
  implementation(libs.converter.moshi)


  implementation(libs.haze)
  implementation(libs.haze.materials)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
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

tasks.withType<com.android.build.gradle.tasks.ExternalNativeBuildTask>().configureEach {
  dependsOn(extractFFmpegNativeLibs)
}
tasks.withType<com.android.build.gradle.tasks.ExternalNativeBuildJsonTask>().configureEach {
  dependsOn(extractFFmpegNativeLibs)
}
