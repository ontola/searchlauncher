package com.searchlauncher.app.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SearchLauncherApp::class)
class WidgetHostViewFactoryTest {
  private lateinit var context: Context
  private lateinit var appWidgetManager: AppWidgetManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    appWidgetManager = AppWidgetManager.getInstance(context)
  }

  /** Binds [appWidgetId] to a provider, as adding a widget through the picker would. */
  private fun bind(appWidgetId: Int, minHeight: Int = 0) {
    val info =
      AppWidgetProviderInfo().apply {
        provider = ComponentName("com.example.widget", "com.example.widget.Provider")
        this.minHeight = minHeight
      }
    shadowOf(appWidgetManager).addBoundWidget(appWidgetId, info)
  }

  @Test
  // A dense screen, or the two readings of the value would agree and prove nothing.
  @Config(qualifiers = "xxhdpi")
  fun `the widget's own minimum height is used as-is, not scaled by density`() {
    // AppWidgetProviderInfo.minHeight is already pixels. Scaling it by the density again made a
    // widget as many times too tall as the screen is dense — Chrome's Dino asks for 110dp, which
    // arrives here as 358px on a 3.25x screen, and was being given 1163px.
    val declaredPx = 358
    bind(11, minHeight = declaredPx)
    val host = AppWidgetHost(context, 1)

    val view =
      WidgetHostViewFactory.createWidgetView(context, 11, host, appWidgetManager) as ViewGroup
    val hostView = view.getChildAt(0)

    val density = context.resources.displayMetrics.density
    assertNotEquals("test needs a density other than 1", 1.0f, density)
    assertEquals(declaredPx, hostView.minimumHeight)
    assertNotEquals((declaredPx * density).toInt(), hostView.minimumHeight)
  }

  @Test
  fun `an id that was never bound cannot be rendered`() {
    // The state an import leaves behind: the id came from a previous install's host, so nothing
    // here answers for it. Rendering it produced an empty view that still took up its space.
    assertFalse(WidgetHostViewFactory.canRender(appWidgetManager, 42))
  }

  @Test
  fun `a bound id can be rendered`() {
    bind(7)

    assertTrue(WidgetHostViewFactory.canRender(appWidgetManager, 7))
  }

  @Test
  fun `binding one id says nothing about another`() {
    bind(7)

    assertTrue(WidgetHostViewFactory.canRender(appWidgetManager, 7))
    assertFalse(WidgetHostViewFactory.canRender(appWidgetManager, 8))
  }
}
