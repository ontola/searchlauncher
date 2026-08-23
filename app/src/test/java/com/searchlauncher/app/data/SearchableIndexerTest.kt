package com.searchlauncher.app.data

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchableIndexerTest {

  @Test
  fun buildDocuments_emptyPackageFilterReturnsEmptyWithoutCatalogWalk() = runBlocking {
    val indexer = SearchableIndexer(ApplicationProvider.getApplicationContext())

    val docs = indexer.buildDocuments(pauseCheck = {}, packageNames = emptyList())

    assertEquals(emptyList<AppSearchDocument>(), docs)
  }

  @Test
  fun documentFrom_buildsActionSearchIntentForTheComponent() {
    val indexer = SearchableIndexer(ApplicationProvider.getApplicationContext<Context>())
    val component = ComponentName("com.android.settings", "com.android.settings.SettingsSearch")
    val doc =
      indexer.documentFrom(
        SearchableActivity(component = component, label = "Settings", hint = "Wi‑Fi, Bluetooth")
      )

    assertEquals(SearchableIndexer.NAMESPACE, doc.namespace)
    assertEquals(component.flattenToString(), doc.id)
    assertEquals("Settings", doc.name)
    assertEquals("Wi‑Fi, Bluetooth", doc.description)

    val intent = Intent.parseUri(doc.intentUri!!, Intent.URI_INTENT_SCHEME)
    assertEquals(Intent.ACTION_SEARCH, intent.action)
    assertEquals(component, intent.component)
    assertEquals("", intent.getStringExtra(SearchManager.QUERY))
  }

  @Test
  fun documentFrom_fallsBackToSearchInLabelWhenHintIsMissing() {
    val indexer = SearchableIndexer(ApplicationProvider.getApplicationContext<Context>())
    val component = ComponentName("com.android.deskclock", "com.android.deskclock.Search")
    val doc =
      indexer.documentFrom(SearchableActivity(component = component, label = "Clock", hint = null))

    assertEquals("Search in Clock", doc.description)
  }

  @Test
  fun queryAfterLabel_requiresAFullLabelBoundary() {
    assertEquals("", SearchableIndexer.queryAfterLabel("Settings", "Settings"))
    assertEquals("wifi", SearchableIndexer.queryAfterLabel("settings wifi", "Settings"))
    assertEquals("foo bar", SearchableIndexer.queryAfterLabel("Play Store foo bar", "Play Store"))
    assertNull(SearchableIndexer.queryAfterLabel("Setting", "Settings"))
    assertNull(SearchableIndexer.queryAfterLabel("SettingsFoo", "Settings"))
    assertNull(SearchableIndexer.queryAfterLabel("clock", "Settings"))
  }

  @Test
  fun searchIntent_putsTheQueryOnTheOfficialExtra() {
    val component = ComponentName("com.example.app", "com.example.app.SearchActivity")
    val intent = SearchableIndexer.searchIntent(component, "alarms")

    assertEquals(Intent.ACTION_SEARCH, intent.action)
    assertEquals(component, intent.component)
    assertEquals("alarms", intent.getStringExtra(SearchManager.QUERY))
  }

  @Test
  fun componentFromId_roundTripsFlattenedComponent() {
    val component = ComponentName("com.android.settings", "com.android.settings.SettingsSearch")
    assertEquals(component, SearchableIndexer.componentFromId(component.flattenToString()))
    assertTrue(SearchableIndexer.componentFromId("not-a-component") == null)
  }
}
