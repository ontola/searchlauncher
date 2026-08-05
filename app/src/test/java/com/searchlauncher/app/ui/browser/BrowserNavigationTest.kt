package com.searchlauncher.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserNavigationTest {
  @Test
  fun keepsHttpUrls() {
    assertEquals("https://example.com/page", browserDestination("https://example.com/page"))
    assertEquals("http://example.com", browserDestination("http://example.com"))
  }

  @Test
  fun addsHttpsToHostNames() {
    assertEquals("https://example.com/page", browserDestination("example.com/page"))
  }

  @Test
  fun turnsOtherInputIntoWebSearch() {
    assertEquals(
      "https://www.google.com/search?q=keyboard+first+launcher",
      browserDestination("keyboard first launcher"),
    )
  }

  @Test
  fun parsesOpaquePageBackgroundColors() {
    assertEquals(0xfff0f1f2.toInt(), parseCssColor("\"rgb(240, 241, 242)\""))
    assertEquals(0x80336699.toInt(), parseCssColor("\"rgba(51, 102, 153, 0.5)\""))
    assertEquals(null, parseCssColor("\"rgba(0, 0, 0, 0)\""))
  }

  @Test
  fun normalizesBrowserOrigins() {
    assertEquals("https://example.com", browserOrigin("https://Example.com/path?q=1"))
    assertEquals("https://example.com:8443", browserOrigin("https://example.com:8443/path"))
    assertEquals(null, browserOrigin("file:///tmp/page.html"))
  }

  @Test
  fun createsDesktopUserAgentWithoutMobileAndroidTokens() {
    assertEquals(
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36",
      desktopUserAgent(
        "Mozilla/5.0 (Linux; Android 16; Phone Build/ABC; wv) AppleWebKit/537.36 Version/4.0 Chrome/140.0 Mobile Safari/537.36"
      ),
    )
  }

  @Test
  fun desktopViewportMetaUsesChromiumDefaultWidth() {
    assertEquals(980, DESKTOP_VIEWPORT_WIDTH)
    assertEquals("width=980, initial-scale=1.0, minimum-scale=0.25", desktopViewportMetaContent())
    assertEquals(
      "width=1280, initial-scale=1.0, minimum-scale=0.25",
      desktopViewportMetaContent(1280),
    )
  }

  @Test
  fun desktopViewportZoomsOutToFitTheScreen() {
    // A 1080px-wide phone at 2.75x is 393 CSS px across, so a 980px layout has to shrink to ~0.4
    // for the whole width to be on screen.
    assertEquals(0.4f, desktopViewportScale(1080, 2.75f), 0.01f)
    assertEquals(
      "width=980, initial-scale=0.401, minimum-scale=0.25",
      desktopViewportMetaContent(scale = desktopViewportScale(1080, 2.75f)),
    )
  }

  @Test
  fun desktopViewportNeverMagnifiesAScreenWideEnoughAlready() {
    // A 2560px tablet at 2x is 1280 CSS px across — wider than the layout, so it stays at 1:1.
    assertEquals(1f, desktopViewportScale(2560, 2f), 0.001f)
    // Degenerate metrics (a WebView measured before layout) must not divide by zero.
    assertEquals(1f, desktopViewportScale(0, 2.75f), 0.001f)
    assertEquals(1f, desktopViewportScale(1080, 0f), 0.001f)
  }

  @Test
  fun desktopViewportDropsTheScaleFloorWhenTheFitIsBelowIt() {
    // A narrow screen fitting below the 0.25 default would otherwise be clamped back up.
    val scale = desktopViewportScale(480, 3f)
    assertEquals(0.163f, scale, 0.01f)
    assertEquals(
      "width=980, initial-scale=0.163, minimum-scale=0.163",
      desktopViewportMetaContent(scale = scale),
    )
  }

  @Test
  fun managesBrowserTabOrderAndActiveTab() {
    val tabs = BrowserTabs("https://one.example")
    tabs.add("https://two.example")
    tabs.add("https://three.example")

    assertEquals(2, tabs.activeIndex)
    assertEquals("https://three.example", tabs.active.url)

    tabs.activate(0)
    assertEquals("https://one.example", tabs.active.url)

    tabs.closeActive()
    assertEquals(2, tabs.items.size)
    assertEquals("https://two.example", tabs.active.url)
  }

  // The two screens the gesture has to feel the same on. Phone: 1200 px at 3.25x, so the fraction
  // gives 216 px and the cap (234 px) never binds. Tablet: 2560 px at 2x, where the fraction would
  // ask for 461 px — a hand's span — and the cap holds it to 144 px.
  private val phone = Screen(widthPx = 1200, density = 3.25f)
  private val tablet = Screen(widthPx = 2560, density = 2f)

  private class Screen(val widthPx: Int, val density: Float)

  private fun Screen.commits(offsetDp: Float, velocityDpPerSecond: Float = 0f) =
    shouldCommitTabSwipe(
      offsetPx = offsetDp * density,
      velocityPxPerSecond = velocityDpPerSecond * density,
      viewportWidthPx = widthPx,
      commitFraction = TAB_COMMIT_FRACTION,
      commitDistanceCapPx = TAB_COMMIT_MAX_DISTANCE.value * density,
      flingVelocityPx = TAB_FLING_VELOCITY.value * density,
    )

  @Test
  fun slowSwipeCommitsPastTheDistanceOnEitherScreen() {
    assertEquals(true, phone.commits(offsetDp = 70f))
    assertEquals(false, phone.commits(offsetDp = 60f))
    // The point of the cap: the same 80 dp that works on a phone works here, where the uncapped
    // fraction would have demanded 230 dp.
    assertEquals(true, tablet.commits(offsetDp = 80f))
    assertEquals(false, tablet.commits(offsetDp = 60f))
  }

  @Test
  fun tabletNoLongerAsksForALongerDragThanThePhone() {
    val phoneThresholdDp = (1..400).first { phone.commits(it.toFloat()) }
    val tabletThresholdDp = (1..400).first { tablet.commits(it.toFloat()) }
    assertEquals(67, phoneThresholdDp)
    assertEquals(72, tabletThresholdDp)
  }

  @Test
  fun flickCommitsWithoutTheDistance() {
    // Barely moved, but thrown: this is the gesture photo viewers page on.
    assertEquals(true, phone.commits(offsetDp = 12f, velocityDpPerSecond = 900f))
    assertEquals(true, tablet.commits(offsetDp = 12f, velocityDpPerSecond = 900f))
    // Same short travel, ambling: nothing happens.
    assertEquals(false, phone.commits(offsetDp = 12f, velocityDpPerSecond = 100f))
  }

  @Test
  fun flickBackTowardsTheStartDoesNotCommit() {
    // Dragged right, thrown left — the user is putting the tab back, not asking for the next one.
    assertEquals(false, phone.commits(offsetDp = 12f, velocityDpPerSecond = -900f))
    assertEquals(false, phone.commits(offsetDp = -12f, velocityDpPerSecond = 900f))
    // But a flick that keeps going the way it was dragged commits in either direction.
    assertEquals(true, phone.commits(offsetDp = -12f, velocityDpPerSecond = -900f))
  }

  @Test
  fun anUntouchedSwipeNeverCommits() {
    assertEquals(false, phone.commits(offsetDp = 0f, velocityDpPerSecond = 5000f))
  }

  @Test
  fun treatsTheSamePageReachedDifferentlyAsAlreadyOpen() {
    val open = listOf("https://example.com/story")
    // The differences that are not differences: scheme, www., a trailing slash, a fragment.
    assertEquals(0, indexOfTabShowing(open, "http://example.com/story"))
    assertEquals(0, indexOfTabShowing(open, "https://www.example.com/story/"))
    assertEquals(0, indexOfTabShowing(open, "https://example.com/story#section-2"))
  }

  @Test
  fun ignoresHowYouArrived() {
    val open = listOf("https://uruguaymeats.uy/en/")
    // The shape of the URL from the earlier bug report: same page, arrived from an advert.
    assertEquals(
      0,
      indexOfTabShowing(
        open,
        "https://uruguaymeats.uy/en/?utm_source=programatica&utm_medium=display" +
          "&gclid=EAIaIQobChMI&gad_source=7",
      ),
    )
  }

  @Test
  fun keepsQueriesThatChooseThePage() {
    val open = listOf("https://example.com/search?q=otters")
    assertEquals(0, indexOfTabShowing(open, "https://example.com/search?q=otters"))
    // Order is not meaning, but the values are.
    assertEquals(
      0,
      indexOfTabShowing(listOf("https://example.com/p?b=2&a=1"), "https://example.com/p?a=1&b=2"),
    )
    assertEquals(-1, indexOfTabShowing(open, "https://example.com/search?q=badgers"))
  }

  @Test
  fun doesNotTreatOneSiteAsOnePage() {
    val open = listOf("https://github.com/foo")
    // The domain-matching idea, declined: same site, different page, and switching to the one
    // already open would land the user somewhere they did not ask for.
    assertEquals(-1, indexOfTabShowing(open, "https://github.com/bar"))
    // Nor are two servers on one host the same place.
    assertEquals(-1, indexOfTabShowing(listOf("http://10.0.0.1:8080/"), "http://10.0.0.1:9090/"))
  }

  @Test
  fun picksTheTabShowingThePage() {
    val open = listOf("https://a.example/one", "https://b.example/two", "https://c.example/three")
    assertEquals(1, indexOfTabShowing(open, "https://b.example/two"))
    assertEquals(-1, indexOfTabShowing(open, "https://d.example/four"))
    assertEquals(-1, indexOfTabShowing(emptyList(), "https://a.example/one"))
  }

  @Test
  fun survivesAddressesThatAreNotUrls() {
    assertEquals(0, indexOfTabShowing(listOf("about:blank"), "about:blank"))
    assertEquals(-1, indexOfTabShowing(listOf("about:blank"), "https://example.com"))
  }

  // Closing a card in the tab strip uses the same rule, with the card's height standing in for the
  // viewport: a quarter of the card, or a flick.
  private fun cardCloses(draggedUpPx: Float, velocityPxPerSecond: Float = 0f) =
    shouldCommitTabSwipe(
      offsetPx = -draggedUpPx,
      velocityPxPerSecond = velocityPxPerSecond,
      viewportWidthPx = 1000,
      commitFraction = 0.25f,
      commitDistanceCapPx = 250f,
      flingVelocityPx = 400f * 3.25f,
    )

  @Test
  fun aFlickClosesACardThatBarelyMoved() {
    // Thrown upwards, a fraction of the way: closed.
    assertEquals(true, cardCloses(draggedUpPx = 40f, velocityPxPerSecond = -2000f))
    // Dragged the same distance and let go gently: it goes back.
    assertEquals(false, cardCloses(draggedUpPx = 40f, velocityPxPerSecond = -100f))
    // A slow drag still closes it once it has covered the ground.
    assertEquals(true, cardCloses(draggedUpPx = 260f))
    // And a flick back down does not close it.
    assertEquals(false, cardCloses(draggedUpPx = 40f, velocityPxPerSecond = 2000f))
  }
}
