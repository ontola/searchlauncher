package com.searchlauncher.app.ui.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast

/** An explicit, complete app list, unaffected by Android's default-browser filtering. */
class ExternalBrowserChooserActivity : Activity() {
  @Suppress("DEPRECATION")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val choices =
      intent
        .getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)
        .orEmpty()
        .filterIsInstance<Intent>()
        .filter { it.component != null && it.component!!.packageName != packageName }
        .mapNotNull { choice ->
          try {
            choice to packageManager.getActivityInfo(choice.component!!, 0)
          } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            null
          }
        }
    if (choices.isEmpty()) {
      Toast.makeText(this, "No browser app installed", Toast.LENGTH_SHORT).show()
      finish()
      return
    }
    val labels = choices.map { it.second.loadLabel(packageManager).toString() }
    val adapter =
      object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
          return (super.getView(position, convertView, parent) as TextView).apply {
            val size = (32 * resources.displayMetrics.density).toInt()
            val icon = choices[position].second.loadIcon(packageManager)
            icon.setBounds(0, 0, size, size)
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = (16 * resources.displayMetrics.density).toInt()
          }
        }
      }
    AlertDialog.Builder(this)
      .setTitle("Open with")
      .setAdapter(adapter) { _, which ->
        try {
          startActivity(choices[which].first)
        } catch (_: ActivityNotFoundException) {
          Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
          Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
        finish()
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
      .setOnCancelListener { finish() }
      .show()
  }
}
