package com.searchlauncher.app.ui.browser

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DownloadTransferTest {
  @Test
  fun recentSpeedIncludesStallsAndRecovers() {
    val tracker = DownloadSpeedTracker()
    assertNull(tracker.sample(0, 10_000_000, 0).bytesPerSecond)
    val progress = tracker.sample(1_000_000, 10_000_000, 1_000)
    assertEquals(1_000_000.0, progress.bytesPerSecond!!, 0.01)
    assertEquals("1.0 MB/s · 9.0 MB left", progress.description(Locale.US))
    tracker.sample(1_000_000, 10_000_000, 2_000)
    tracker.sample(1_000_000, 10_000_000, 3_000)
    assertEquals(0.0, tracker.sample(1_000_000, 10_000_000, 4_000).bytesPerSecond!!, 0.0)
    assertTrue(tracker.sample(2_000_000, 10_000_000, 4_500).bytesPerSecond!! > 0)
  }

  @Test
  fun unknownOrExceededLengthsNeverInventRemainingBytes() {
    for (total in listOf(null, -1L, 100L)) {
      val stats = DownloadTransfer(2_000_000, total, 250_000.0)
      assertNull(stats.remaining)
      assertNull(stats.fraction)
      assertEquals("250 KB/s · 2.0 MB downloaded · Size unknown", stats.description(Locale.US))
    }
    assertEquals("0.0 MB left", DownloadTransfer(0, 0, null).description(Locale.US))
  }

  @Test
  fun counterOrClockResetStartsNewMeasurement() {
    val tracker = DownloadSpeedTracker()
    tracker.sample(100, null, 100)
    assertNull(tracker.sample(50, null, 200).bytesPerSecond)
    assertNull(tracker.sample(80, null, 150).bytesPerSecond)
    assertNull(tracker.sample(80, null, 150).bytesPerSecond)
  }

  @Test
  fun remainingUsesMegabytesEvenForLargeFiles() {
    assertEquals(
      "2.5 MB/s · 1500.0 MB left",
      DownloadTransfer(500_000_000, 2_000_000_000, 2_500_000.0).description(Locale.US),
    )
    assertEquals(
      "2,5 MB/s · 1500,0 MB left",
      DownloadTransfer(500_000_000, 2_000_000_000, 2_500_000.0).description(Locale.GERMANY),
    )
  }
}
