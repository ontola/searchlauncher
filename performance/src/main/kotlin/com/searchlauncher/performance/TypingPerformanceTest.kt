package com.searchlauncher.performance

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the installed APK unchanged, including old releases used for comparison. Complete
 * onboarding and enable the built-in keyboard before running on a test device. Keep animations
 * enabled. Timing is captured separately with Perfetto/gfxinfo.
 */
@RunWith(AndroidJUnit4::class)
class TypingPerformanceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val device = UiDevice.getInstance(instrumentation)
  private val pkg = targetBrowserPackage

  private fun home() {
    device.wakeUp()
    device.executeShellCommand("wm dismiss-keyguard")
    instrumentation.context.startActivity(
      Intent()
        .setClassName(pkg, "com.searchlauncher.app.ui.MainActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000)!!.text = ""
    assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 10000))
  }

  @Test
  fun firstResultLatency() {
    home()
    repeat(12) { round ->
      val letter = listOf("s", "c", "w")[round % 3]
      device.findObject(By.text(letter))!!.click()
      SystemClock.sleep(500)
      assertEquals(letter, device.findObject(By.clazz("android.widget.EditText"))!!.text)
      device.pressKeyCode(KeyEvent.KEYCODE_DEL)
      SystemClock.sleep(500)
      assertEquals("", device.findObject(By.clazz("android.widget.EditText"))!!.text)
    }
  }

  @Test
  fun optimizedBrowserNavigationAndDownload() {
    val server = java.net.ServerSocket(0)
    val payload = "SearchLauncher optimized download".toByteArray()
    val fileName = "typing-audit-${server.localPort}.txt"
    val secondPageRequests = java.util.concurrent.atomic.AtomicInteger()
    kotlin.concurrent.thread(isDaemon = true) {
      while (!server.isClosed) {
        val socket = runCatching { server.accept() }.getOrNull() ?: break
        socket.use {
          val reader = it.getInputStream().bufferedReader()
          val path = reader.readLine()?.split(" ")?.getOrNull(1)
          if (path == "/second") secondPageRequests.incrementAndGet()
          while (!reader.readLine().isNullOrEmpty()) {}
          val body =
            if (path == "/download") payload
            else
              """<!doctype html><meta name="viewport" content="width=device-width">
            <meta name="theme-color" content="#aa1239"><title>Optimized browser fixture</title>
            <h1>${if (path == "/second") "Second page" else "First page"}</h1>
            <p><a href="/second">Next fixture page</a></p>
            <p><a href="/download" download="$fileName">Download fixture</a></p>"""
                .toByteArray()
          val type = if (path == "/download") "application/octet-stream" else "text/html"
          val attachment =
            if (path == "/download") "Content-Disposition: attachment; filename=$fileName\r\n"
            else ""
          it
            .getOutputStream()
            .write(
              ("HTTP/1.1 200 OK\r\nContent-Type: $type\r\n$attachment" +
                  "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                .toByteArray() + body
            )
        }
      }
    }
    try {
      instrumentation.context.startActivity(
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://localhost:${server.localPort}/"))
          .setClassName(pkg, "com.searchlauncher.app.ui.browser.BrowserActivity")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      assertNotNull(device.wait(Until.findObject(By.text("First page")), 15000))
      device.findObject(By.text("Next fixture page"))!!.click()
      assertNotNull(device.wait(Until.findObject(By.text("Second page")), 10000))
      device.findObject(By.desc("Browser menu"))!!.click()
      device.wait(Until.findObject(By.text("Reload")), 5000)!!.click()
      val reloadDeadline = SystemClock.uptimeMillis() + 10000
      while (secondPageRequests.get() < 2 && SystemClock.uptimeMillis() < reloadDeadline) {
        SystemClock.sleep(50)
      }
      assertTrue("Reload must request the document again", secondPageRequests.get() >= 2)
      assertNotNull(device.wait(Until.findObject(By.text("Second page")), 10000))
      device.findObject(By.text("Download fixture"))!!.click()
      assertNotNull(device.wait(Until.findObject(By.text(fileName)), 10000))
      assertNotNull(device.wait(Until.findObject(By.text("Complete")), 10000))
      device.takeScreenshot(
        File(instrumentation.context.getExternalFilesDir(null), "optimized-download.png")
      )
    } finally {
      server.close()
    }
  }

  @Test
  fun navigationBarTracksSwipeAndCancellation() {
    home()
    SystemClock.sleep(500)
    val homeColor = navigationColor("home")
    // Reuse the deterministic browser fixture setup; it leaves a fresh tab available.
    optimizedBrowserNavigationAndDownload()
    device.wait(Until.findObject(By.text("Done")), 5000)!!.click()
    val browserBar = device.wait(Until.findObject(By.desc("Browser menu")), 5000)!!.visibleBounds
    SystemClock.sleep(300)
    val browserColor = navigationColor("browser")
    assertTrue(
      "Fixture and home must have different colors",
      colorDistance(homeColor, browserColor) > 25,
    )

    fun exercise(start: Float, direction: Float, y: Int, from: Int, to: Int, prefix: String) {
      val width = device.displayWidth.toFloat()
      val down = SystemClock.uptimeMillis()
      fun touch(action: Int, x: Float) {
        val event =
          MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x * width, y.toFloat(), 0)
            .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
          assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
          event.recycle()
        }
        SystemClock.sleep(100)
      }
      touch(MotionEvent.ACTION_DOWN, start)
      try {
        touch(MotionEvent.ACTION_MOVE, start + direction * 0.18f)
        val early = navigationColor("$prefix-early")
        touch(MotionEvent.ACTION_MOVE, start + direction * 0.40f)
        val middle = navigationColor("$prefix-middle")
        assertTrue("Color must move before releasing the swipe", colorDistance(from, early) > 5)
        assertTrue(
          "Color must continue blending",
          colorDistance(from, middle) > colorDistance(from, early),
        )
        assertTrue("Halfway must not snap to destination", colorDistance(middle, to) > 5)
        touch(MotionEvent.ACTION_MOVE, start + direction * 0.10f)
        val reversed = navigationColor("$prefix-reversed")
        assertTrue(
          "Reversing must restore the source color",
          colorDistance(from, reversed) < colorDistance(from, middle),
        )
        touch(MotionEvent.ACTION_MOVE, start)
      } finally {
        touch(MotionEvent.ACTION_UP, start)
      }
      SystemClock.sleep(500)
      assertTrue(
        "Cancelled swipe restores its color",
        colorDistance(from, navigationColor("$prefix-cancelled")) < 4,
      )
    }
    exercise(0.80f, -1f, browserBar.centerY(), browserColor, homeColor, "to-home")
    device.swipe(
      device.displayWidth * 8 / 10,
      browserBar.centerY(),
      device.displayWidth / 10,
      browserBar.centerY(),
      40,
    )
    assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 10000))
    SystemClock.sleep(500)
    assertTrue(colorDistance(homeColor, navigationColor("home-committed")) < 4)
    val homeBar = device.findObject(By.clazz("android.widget.EditText"))!!.visibleBounds
    exercise(0.20f, 1f, homeBar.centerY(), homeColor, browserColor, "to-tab")
    device.swipe(
      device.displayWidth / 5,
      homeBar.centerY(),
      device.displayWidth * 9 / 10,
      homeBar.centerY(),
      40,
    )
    assertNotNull(device.wait(Until.findObject(By.desc("Browser menu")), 10000))
    SystemClock.sleep(500)
    assertTrue(colorDistance(browserColor, navigationColor("tab-committed")) < 4)
  }

  private fun navigationColor(label: String): Int {
    val file = File(instrumentation.context.getExternalFilesDir(null), "nav-$label.png")
    assertTrue(device.takeScreenshot(file))
    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
    return try {
      bitmap.getPixel(bitmap.width / 2, bitmap.height - 8)
    } finally {
      bitmap.recycle()
    }
  }

  private fun colorDistance(a: Int, b: Int): Int =
    kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
      kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
      kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))

  @Test
  fun continuousTyping() {
    home()
    val words = listOf("settings", "camera", "clock")
    val keys =
      words.joinToString("").toSet().associateWith { letter ->
        device.findObject(By.text(letter.toString()))!!.visibleBounds
      }
    val frames = StringBuilder()
    repeat(3) { cycle ->
      words.forEach { word ->
        device.executeShellCommand("dumpsys gfxinfo $pkg reset")
        word.forEach { letter ->
          val bounds = keys.getValue(letter)
          val down = SystemClock.uptimeMillis()
          for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event =
              MotionEvent.obtain(
                  down,
                  SystemClock.uptimeMillis(),
                  action,
                  bounds.centerX().toFloat(),
                  bounds.centerY().toFloat(),
                  0,
                )
                .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
              assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
              event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(16)
          }
          SystemClock.sleep(84)
        }
        SystemClock.sleep(250)
        assertEquals(
          "Typed word, cycle $cycle",
          word,
          device.findObject(By.clazz("android.widget.EditText"))!!.text,
        )
        if (word == "settings")
          assertNotNull(device.wait(Until.findObject(By.text("Settings")), 3000))
        repeat(word.length) {
          device.pressKeyCode(KeyEvent.KEYCODE_DEL)
          SystemClock.sleep(50)
        }
        SystemClock.sleep(250)
        assertNotNull(
          device.wait(Until.findObject(By.clazz("android.widget.EditText").text("")), 3000)
        )
        frames
          .appendLine("cycle=$cycle word=$word")
          .appendLine(device.executeShellCommand("dumpsys gfxinfo $pkg framestats"))
      }
    }
    File(instrumentation.context.getExternalFilesDir(null), "typing-frames.txt")
      .writeText(frames.toString())
    device.takeScreenshot(File(instrumentation.context.getExternalFilesDir(null), "typing.png"))
  }
}
