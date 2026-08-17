package com.searchlauncher.app.data

import android.os.Process
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IndexingDispatchersTest {

  @Test
  fun limitedDispatcher_runsBelowDefaultPriority() = runBlocking {
    val priority =
      withContext(IndexingDispatchers.limited) { Process.getThreadPriority(Process.myTid()) }

    assertTrue(
      "Indexing must yield CPU to the UI thread; default-priority IO work is why background " +
        "rebuilds still hitch typing",
      priority >= Process.THREAD_PRIORITY_BACKGROUND,
    )
  }
}
