package com.searchlauncher.app.ui.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstalledWebAppsTest {
  private val document = "https://app.example/app/page"

  private fun manifest() =
    JSONObject()
      .put("name", "Example app")
      .put("start_url", "https://app.example/app/start")
      .put("scope", "https://app.example/app/")
      .put("display", "standalone")

  @Test
  fun scopeIncludesOnlyTheInstalledOriginAndPath() {
    val app = InstalledWebApp.fromManifest(manifest(), document)!!
    assertTrue(app.contains("https://app.example/app/settings?tab=1#privacy"))
    assertTrue(app.contains("https://app.example:443/app/"))
    for (url in
      listOf(
        "https://evil.example/app/",
        "https://app.example:444/app/",
        "https://app.example/apple/",
        "https://app.example/app/../login",
        "https://app.example/app/%2e%2e/login",
        "https://app.example@evil.example/app/",
        "http://app.example/app/",
        "javascript:alert(1)",
      )) {
      assertFalse(url, app.contains(url))
    }
  }

  @Test
  fun unsafeOrUnsupportedManifestsCannotHideBrowserControls() {
    assertNull(
      InstalledWebApp.fromManifest(
        manifest().put("start_url", "https://evil.example/app/"),
        document,
      )
    )
    assertNull(
      InstalledWebApp.fromManifest(manifest().put("scope", "https://evil.example/"), document)
    )
    assertNull(
      InstalledWebApp.fromManifest(
        manifest().put("start_url", "https://app.example/outside"),
        document,
      )
    )
    assertNull(InstalledWebApp.fromManifest(manifest().put("display", "browser"), document))
    assertNull(InstalledWebApp.fromManifest(manifest().put("name", ""), document))
    assertNull(InstalledWebApp.fromManifest(manifest().put("start_url", "intent://app/"), document))
  }

  @Test
  fun installSurvivesTabClosureAndRemovalRestoresNormalBrowsing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = InstalledWebApp.fromManifest(manifest(), document)!!
    InstalledWebApps.save(context, app)
    val intent = InstalledWebApps.launchIntent(context, app)
    val tabId = BrowserTabTasks.tabIdOf(intent)!!
    assertEquals(app.id, intent.getStringExtra(InstalledWebApps.EXTRA_APP_ID))
    BrowserTabStore.clear()
    assertEquals(app, InstalledWebApps.get(context, app.id))
    val newIntent = InstalledWebApps.launchIntent(context, app)
    assertNotEquals(tabId, BrowserTabTasks.tabIdOf(newIntent))
    assertEquals(newIntent.data, InstalledWebApps.launchIntent(context, app).data)
    InstalledWebApps.remove(context, app)
    assertNull(InstalledWebApps.get(context, app.id))
    assertNull(BrowserTabStore.lastTab()!!.installedApp)
    BrowserTabStore.clear()
  }
}
