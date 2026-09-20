/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the LICENSE file
 * in the repo root of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-added web apps: home-grid tiles that open a URL fullscreen in Immortal's
 * built-in WebView ([WebAppActivity]).
 *
 * Why this exists: the Portal's stock Chromium can't mint WebAPKs (no Google
 * services), and its shortcut fallback uses the legacy INSTALL_SHORTCUT
 * broadcast, which Android 10 drops system-wide — so "Install app" in the
 * browser silently does nothing. Web-app tiles give PWAs/sites a first-class
 * presence on the grid, managed entirely on-device from Manage mode.
 *
 * Entries are persisted as a JSON array in SharedPreferences, in the same
 * low-ceremony style as [UserLayout]/[HomeWidgetStore].
 */
object WebAppStore {

  data class WebApp(val id: String, val label: String, val url: String)

  /**
   * Pseudo-package prefix for web-app tiles. [android.content.ComponentName]
   * identity on the grid is `component.packageName`, so each web app gets a
   * unique synthetic package (`webapp.<id>`); the launch/uninstall dispatch in
   * HomeActivity recognises the prefix and routes to this store instead of the
   * PackageManager. Also works as a folder-assignment key in [UserLayout].
   */
  const val PKG_PREFIX = "webapp."

  private const val PREFS = "immortal_webapps"
  private const val KEY = "webapps"

  private val listeners = mutableListOf<() -> Unit>()

  fun addListener(l: () -> Unit) {
    listeners.add(l)
  }

  fun removeListener(l: () -> Unit) {
    listeners.remove(l)
  }

  private fun notifyChanged() {
    listeners.toList().forEach { runCatching(it) }
  }

  fun load(context: Context): List<WebApp> =
      runCatching {
            val raw =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                    ?: return emptyList()
            val arr = JSONArray(raw)
            buildList {
              for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val label = o.optString("label").trim()
                val url = o.optString("url").trim()
                if (id.isBlank() || label.isBlank() || url.isBlank()) continue
                add(WebApp(id, label, normalize(url)))
              }
            }
          }
          .getOrDefault(emptyList())

  fun save(context: Context, apps: List<WebApp>) {
    val arr = JSONArray()
    apps.forEach {
      arr.put(JSONObject().put("id", it.id).put("label", it.label).put("url", it.url))
    }
    context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY, arr.toString())
        .apply()
    notifyChanged()
  }

  fun add(context: Context, label: String, url: String): WebApp {
    val apps = load(context).toMutableList()
    val app = WebApp(freshId(apps), label.trim(), normalize(url))
    apps.add(app)
    save(context, apps)
    return app
  }

  fun remove(context: Context, id: String) {
    save(context, load(context).filterNot { it.id == id })
  }

  fun rename(context: Context, id: String, label: String) {
    save(
        context,
        load(context).map {
          if (it.id == id) it.copy(label = label.trim().ifEmpty { it.label }) else it
        })
  }

  /** Tolerant URL normalisation: bare domains get https:// prepended. */
  fun normalize(url: String): String {
    val u = url.trim()
    return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
  }

  /** Extracts the web-app id from a synthetic package name, or null if not one. */
  fun idFromPackage(pkg: String): String? =
      if (pkg.startsWith(PKG_PREFIX)) pkg.removePrefix(PKG_PREFIX) else null

  private fun freshId(existing: List<WebApp>): String {
    val used = existing.map { it.id }.toSet()
    var i = 1
    while (i.toString() in used) i++
    return i.toString()
  }
}
