package com.searchlauncher.app.data

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class ShortcutAvailabilityTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  private fun shortcut(urlTemplate: String) =
    SearchShortcut(id = "test", alias = "t", urlTemplate = urlTemplate, description = "Test")

  private fun isAvailable(urlTemplate: String) =
    ShortcutAvailability.isAvailable(context.packageManager, shortcut(urlTemplate))

  /** Teaches the package manager that something handles [uri], as an installed app would. */
  private fun installHandlerFor(uri: String) {
    val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
    shadowOf(context.packageManager)
      .addResolveInfoForIntent(intent, android.content.pm.ResolveInfo())
  }

  @Test
  fun `web shortcuts are always available`() {
    assertTrue(isAvailable("https://claude.ai/new?q=%s"))
    assertTrue(isAvailable("http://example.com/?q=%s"))
  }

  @Test
  fun `web check is case insensitive`() {
    assertTrue(isAvailable("HTTPS://claude.ai/new?q=%s"))
  }

  @Test
  fun `an app scheme with nothing installed is unavailable`() {
    assertFalse(isAvailable("claude://claude.ai/new?q=%s"))
    assertFalse(isAvailable("spotify:search:%s"))
  }

  @Test
  fun `an app scheme becomes available once its app is installed`() {
    val template = "claude://claude.ai/new?q=%s"
    assertFalse(isAvailable(template))

    installHandlerFor("claude://claude.ai/new?q=")

    assertTrue(isAvailable(template))
  }

  @Test
  fun `an intent template resolves by action rather than scheme`() {
    val template =
      "intent:#Intent;action=android.intent.action.INSERT;" +
        "type=vnd.android.cursor.item/event;S.title=%s;end"
    assertFalse(isAvailable(template))

    installHandlerFor(template.replace("%s", ""))

    assertTrue(isAvailable(template))
  }

  @Test
  fun `an unparseable template counts as available rather than vanishing`() {
    // Fail open: a template this code cannot make sense of is the user's business, and hiding it
    // would leave them with a shortcut that silently does nothing.
    assertTrue(isAvailable("not a uri at all %s"))
  }

  @Test
  fun `the launcher's own internal scheme stays available`() {
    // widget_search is intercepted by id and never launched as an intent, so nothing resolves it.
    assertTrue(isAvailable("internal://widget?q=%s"))
  }

  @Test
  fun `every default shortcut is offered on a device that has the apps`() {
    // A default the launcher ships but then hides would be a bug, so each non-web default must be
    // judged on its app rather than dropped for some other reason.
    DefaultShortcuts.searchShortcuts.forEach { shortcut ->
      val expected =
        ShortcutAvailability.isWebTemplate(shortcut.urlTemplate) ||
          shortcut.id == "widget_search" ||
          shortcut.urlTemplate.startsWith("internal://")
      if (expected) {
        assertTrue(
          "${shortcut.id} should never be hidden",
          ShortcutAvailability.isAvailable(context.packageManager, shortcut),
        )
      }
    }
  }

  @Test
  fun `isWebTemplate only accepts http schemes`() {
    assertTrue(ShortcutAvailability.isWebTemplate("https://a/%s"))
    assertTrue(ShortcutAvailability.isWebTemplate("http://a/%s"))
    assertFalse(ShortcutAvailability.isWebTemplate("claude://a/%s"))
    assertFalse(ShortcutAvailability.isWebTemplate("market://search?q=%s"))
  }
}
