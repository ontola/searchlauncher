package com.searchlauncher.app.fixture

class AppLinkReceiverActivity : android.app.Activity() {
  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(
      android.widget.TextView(this).apply {
        text = "Received app link: ${intent.dataString}"
        textSize = 18f
      }
    )
  }
}
