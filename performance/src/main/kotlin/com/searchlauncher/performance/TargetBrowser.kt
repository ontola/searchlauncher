package com.searchlauncher.performance

import androidx.test.platform.app.InstrumentationRegistry

internal val targetBrowserPackage: String
  get() =
    InstrumentationRegistry.getArguments().getString("targetPackage")
      ?: "com.searchlauncher.app.gecko"
