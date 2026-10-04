plugins {
  id("com.android.test")
  id("org.jetbrains.kotlin.android")
  id("com.diffplug.spotless") version "6.25.0"
}

android {
  namespace = "com.searchlauncher.performance"
  compileSdk = 37
  defaultConfig {
    minSdk = 29
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  targetProjectPath = ":app"
  // Run in a separate process: no test dependencies or keep rules in the measured app.
  experimentalProperties["android.experimental.self-instrumenting"] = true
  buildTypes {
    create("gecko") {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

androidComponents { beforeVariants { it.enable = it.buildType == "gecko" } }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
  implementation("androidx.test:runner:1.7.0")
  implementation("androidx.test.ext:junit:1.3.0")
  implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktfmt("0.47").googleStyle()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt("0.47").googleStyle()
  }
}
