package com.searchlauncher.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppIndexerTest {

  @Test
  fun buildDocuments_emptyPackageFilterReturnsEmptyWithoutCatalogWalk() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val indexer = AppIndexer(context)

    val docs = indexer.buildDocuments(pauseCheck = {}, packageNames = emptyList())

    assertTrue(docs.isEmpty())
  }
}
