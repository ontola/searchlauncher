plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp")
  id("org.jetbrains.kotlin.plugin.compose")
  id("kotlin-kapt")
  id("com.diffplug.spotless") version "6.25.0"
}

android {
  namespace = "com.searchlauncher.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.searchlauncher.app"
    minSdk = 29
    targetSdk = 36
    versionCode = 1
    versionName = "0.0.1-beta"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
  }

  signingConfigs {
    create("release") {
      storeFile = file("upload.jks")
      storePassword = "password"
      keyAlias = "upload"
      keyPassword = "password"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true }

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
}
