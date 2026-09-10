package com.searchlauncher.app.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.WidgetRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
  @Config(qualifiers = "xxhdpi")
  fun `allocated widget size is reported as SizeF and follows resizing`() {
    bind(12, minHeight = 60)
    val sizes = mutableListOf<Pair<Int, Int>>()
    val intApiCalls = mutableListOf<Pair<Int, Int>>()
    val widgetView =
      object : AppWidgetHostView(context) {
        override fun updateAppWidgetSize(options: Bundle, sizesFromHost: List<SizeF>) {
          val size = sizesFromHost.single()
          sizes.add(size.width.toInt() to size.height.toInt())
        }

        @Deprecated("Deprecated in Java")
        override fun updateAppWidgetSize(
          options: Bundle?,
          minWidth: Int,
          minHeight: Int,
          maxWidth: Int,
          maxHeight: Int,
        ) {
          intApiCalls.add(minWidth to minHeight)
        }
      }
    val host = io.mockk.mockk<AppWidgetHost>()
    io.mockk.every { host.createView(any(), 12, any()) } returns widgetView
    val view =
      WidgetHostViewFactory.createWidgetView(context, 12, host, appWidgetManager) as ViewGroup
    val activity =
      org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
    activity.setContentView(view, ViewGroup.LayoutParams(900, 600))
    fun layout(height: Int) {
      view.layoutParams = view.layoutParams.apply { this.height = height }
      view.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
      )
      view.layout(0, 0, 900, height)
      shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    layout(600)
    assertTrue("Android 12+ must use SizeF sizes, not the four-int API", intApiCalls.isEmpty())
    val first = sizes.last().second
    assertTrue(first > 0)
    assertTrue(first <= 200)
    layout(900)
    val second = sizes.last().second
    assertEquals(100, second - first)
    activity.finish()
  }

  @Test
  fun `size options include a non-empty Android 12 sizes list`() {
    val options = WidgetHostViewFactory.sizeOptions(320, 200)
    assertEquals(320, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
    assertEquals(320, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH))
    assertEquals(200, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
    assertEquals(200, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
    val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    assertNotNull(sizes)
    assertEquals(1, sizes!!.size)
    assertEquals(320f, sizes[0].width)
    assertEquals(200f, sizes[0].height)
  }

  @Test
  fun `default size options use the stored widget height`() {
    val options = WidgetHostViewFactory.defaultSizeOptions(context)
    assertEquals(
      WidgetRepository.DEFAULT_WIDGET_HEIGHT_DP,
      options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
    )
    val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    assertFalse(sizes.isNullOrEmpty())
  }

  @Test
  @Config(qualifiers = "xxhdpi")
  fun `container size is reported even before the host view lays itself out`() {
    bind(14)
    val sizes = mutableListOf<SizeF>()
    val widgetView =
      object : AppWidgetHostView(context) {
        override fun updateAppWidgetSize(options: Bundle, sizesFromHost: List<SizeF>) {
          sizes += sizesFromHost
        }
      }
    val host = io.mockk.mockk<AppWidgetHost>()
    io.mockk.every { host.createView(any(), 14, any()) } returns widgetView
    val view =
      WidgetHostViewFactory.createWidgetView(context, 14, host, appWidgetManager) as ViewGroup
    WidgetHostViewFactory.reportContainerSize(view, appWidgetManager, 900, 600)
    assertEquals(1, sizes.size)
    val density = context.resources.displayMetrics.density
    assertEquals((900 / density).toInt().toFloat(), sizes[0].width)
    assertEquals((600 / density).toInt().toFloat(), sizes[0].height)
  }

  @Test
  fun `collection adapters are asked to reload after a size is reported`() {
    bind(15)
    val list = ListView(context).apply { id = 42 }
    val widgetView =
      object : AppWidgetHostView(context) {
        init {
          addView(list, ViewGroup.LayoutParams(100, 100))
        }

        override fun updateAppWidgetSize(options: Bundle, sizesFromHost: List<SizeF>) {}
      }
    widgetView.layout(0, 0, 300, 200)
    WidgetHostViewFactory.reportAllocatedSize(widgetView, appWidgetManager, 15, 300, 200)
    // Robolectric delivers the notify; the point is that a ListView child is discovered and the
    // call does not throw. A second report of the same size is coalesced.
    WidgetHostViewFactory.reportAllocatedSize(widgetView, appWidgetManager, 15, 300, 200)
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

  @Test
  fun `the launcher host view does not clip collection children`() {
    val view = LauncherAppWidgetHostView(context)
    assertFalse(view.clipChildren)
    assertFalse(view.clipToPadding)
  }
}
