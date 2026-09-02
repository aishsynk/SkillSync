import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Release signing: a fixed key so every published APK carries the same
// certificate and Android will always offer "update" over the previously
// installed build instead of demanding an uninstall first. Credentials live
// in keystore.properties (git-ignored) — see keystore/README.md.
val keystoreProps = Properties()
run {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { keystoreProps.load(it) }
}

android {
    namespace = "com.example.skillsync"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.skillsync"
        minSdk = 24
        targetSdk = 34
        versionCode = 155
        versionName = "3.67.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = if (keystoreProps.containsKey("storeFile")) {
                rootProject.file(keystoreProps["storeFile"] as String)
            } else {
                rootProject.file("keystore/skillsync-release.jks")
            }
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword", "ZKawzv4nwYFf4OPGeeHe5yz3")
                keyAlias = keystoreProps.getProperty("keyAlias", "skillsync-release")
                keyPassword = keystoreProps.getProperty("keyPassword", "ZKawzv4nwYFf4OPGeeHe5yz3")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Sign debug builds with the exact same release keystore so that local builds,
            // debug APKs, and release APKs share an identical certificate and can update
            // seamlessly over each other without ever prompting for an uninstall!
            val relConfig = signingConfigs.findByName("release")
            if (relConfig?.storeFile?.exists() == true) {
                signingConfig = relConfig
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val relConfig = signingConfigs.findByName("release")
            if (relConfig?.storeFile?.exists() == true) {
                signingConfig = relConfig
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time is API 26; this app ships to minSdk 24. Desugaring backports
        // it rather than the alternative of hand-rolling date maths on Calendar,
        // which is where date bugs come from.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
      unitTests {
        // Robolectric renders real Compose UI in JVM tests, which needs resources.
        isIncludeAndroidResources = true
        isReturnDefaultValues = true
      }
    }
}

kotlin {
    jvmToolchain(17)
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
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Render Compose screens on the JVM so layout/runtime faults are caught
  // without a device — this project has no emulator available.
  testImplementation(composeBom)
  testImplementation("org.robolectric:robolectric:4.16")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.test.ext.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Networking (Retrofit & OkHttp)
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-gson:2.11.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

  // WorkManager for background notifications
  implementation("androidx.work:work-runtime-ktx:2.9.0")

  // Backports java.time to API 24 — see isCoreLibraryDesugaringEnabled above.
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

  // Trainer profile photos come from RMS as plain URLs.
  implementation(libs.coil.compose)
}
