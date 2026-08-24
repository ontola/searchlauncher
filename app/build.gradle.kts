plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp")
  id("org.jetbrains.kotlin.plugin.compose")
  id("kotlin-kapt")
  id("com.diffplug.spotless") version "6.25.0"
}

// Falls back to an empty string rather than failing configuration, so building outside a git
// checkout — from a source tarball, say — still works.
fun runGit(vararg args: String): String =
  runCatching {
      providers
        .exec {
          commandLine("git", *args)
          workingDir = projectDir
        }
        .standardOutput
        .asText
        .get()
        .trim()
    }
    .getOrDefault("")

val gitHash = runGit("rev-parse", "--short", "HEAD").ifEmpty { "unknown" }
val buildDate = runGit("log", "-1", "--format=%cs").ifEmpty { "unknown" }
val releaseKeyStorePath = System.getenv("SIGNING_KEY_STORE_PATH") ?: "upload.jks"
val releaseKeyStoreFile = file(releaseKeyStorePath)
val hasReleaseSigning = releaseKeyStoreFile.exists()

android {
  namespace = "com.searchlauncher.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.searchlauncher.app"
    minSdk = 29
    targetSdk = 36
    // F-Droid greps these two literals out of this file to notice new release tags, so
    // they have to stay plain literals and be bumped in the commit that gets tagged. The series
    // starts at 250 to clear 242, the highest the old commit-count scheme ever shipped.
    versionCode = 258
    versionName = "0.0.20"

    buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
    buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = releaseKeyStoreFile
        storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "password"
        keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "password"
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      // Shows up wherever the version does - the settings screen, Android's app info - so the two
      // installs can be told apart there as well as by their name.
      versionNameSuffix = "-debug"
    }
    release {
      // Always on, and deliberately not behind a property. F-Droid builds a plain
      // `assembleRelease`, so anything only our CI passes would make its APK differ from the
      // released one and fail reproducible-build verification.
      isMinifyEnabled = true
      isShrinkResources = true
      // AGP otherwise writes the checkout's git details into the APK, and what it finds depends on
      // how the tree was obtained: a clone gets a revision, a worktree gets NO_VALID_GIT_FOUND.
      // That difference alone breaks reproducible builds. BuildConfig.GIT_HASH still records it.
      vcsInfo { include = false }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures {
    compose = true
    buildConfig = true
  }

  // AGP otherwise signs a dependency list into the APK for Google Play's benefit. F-Droid's
  // scanner rejects it as an extra signing block, and it tells our users nothing.
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**/*.kt")
    ktfmt("0.47").googleStyle()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt("0.47").googleStyle()
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
  implementation("androidx.savedstate:savedstate-ktx:1.4.0")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation(platform("androidx.compose:compose-bom:2025.11.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.documentfile:documentfile:1.0.1")

  // AppSearch
  implementation("androidx.appsearch:appsearch:1.1.0")
  implementation("androidx.appsearch:appsearch-local-storage:1.1.0")
  implementation("androidx.appsearch:appsearch-platform-storage:1.1.0")
  implementation("androidx.test:core-ktx:1.7.0")
  kapt("androidx.appsearch:appsearch-compiler:1.1.0")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

  // DataStore
  implementation("androidx.datastore:datastore-preferences:1.1.7")

  // Palette for color extraction
  implementation("androidx.palette:palette-ktx:1.0.0")

  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
  androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.00"))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  implementation("io.coil-kt:coil-compose:2.5.0")
  testImplementation("org.robolectric:robolectric:4.16")
  testImplementation("io.mockk:mockk:1.14.6")
  implementation("io.sentry:sentry-android:7.16.0")
}
