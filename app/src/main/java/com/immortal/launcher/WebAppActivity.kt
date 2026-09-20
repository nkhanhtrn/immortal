/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the LICENSE file
 * in the repo root of this source tree.
 */

package com.immortal.launcher

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Fullscreen WebView shell for a [WebAppStore] tile. Immersive, keeps the
 * immersive bars hidden on focus regain (same re-assert as HomeActivity), back
 * navigates web history first and exits second. Non-http(s) links (mailto:,
 * intent:, …) are handed to the system rather than erroring inside the WebView.
 *
 * Uses the Portal's own WebView provider (com.facebook.portal.webview), so no
 * browser dependency and no Google services involved.
 */
class WebAppActivity : ComponentActivity() {

  private var webView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val url = intent.getStringExtra(EXTRA_URL)
    if (url.isNullOrBlank()) {
      finish()
      return
    }
    val label = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.app_name)
    setTitle(label)

    val wv = WebView(this)
    configure(wv)
    if (savedInstanceState != null) wv.restoreState(savedInstanceState) else wv.loadUrl(url)
    setContentView(wv)
    webView = wv

    onBackPressedDispatcher.addCallback(this) {
      if (wv.canGoBack()) wv.goBack() else finish()
    }

    // The frame shouldn't doze mid-session; the user exits explicitly via Back/Home.
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    applyImmersive()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) applyImmersive()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    webView?.saveState(outState)
  }

  override fun onDestroy() {
    webView?.destroy()
    webView = null
    super.onDestroy()
  }

  private fun applyImmersive() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    @Suppress("DEPRECATION")
    window.statusBarColor = Color.TRANSPARENT
    @Suppress("DEPRECATION")
    window.navigationBarColor = Color.TRANSPARENT
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun configure(wv: WebView) {
    wv.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      cacheMode = WebSettings.LOAD_DEFAULT
      useWideViewPort = true
      loadWithOverviewMode = true
      builtInZoomControls = false
      mediaPlaybackRequiresUserGesture = false
      // PWAs commonly still reference http assets; don't hard-fail them.
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
    wv.setBackgroundColor(Color.BLACK)
    wv.overScrollMode = View.OVER_SCROLL_NEVER
    wv.webViewClient =
        object : WebViewClient() {
          override fun shouldOverrideUrlLoading(
              view: WebView,
              request: WebResourceRequest,
          ): Boolean {
            val u = request.url
            // Keep web navigation inside the shell; other schemes go to the system.
            if (u.scheme == "http" || u.scheme == "https") return false
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
            return true
          }
        }
    wv.webChromeClient = WebChromeClient()
  }

  companion object {
    const val EXTRA_URL = "com.immortal.launcher.extra.URL"
    const val EXTRA_LABEL = "com.immortal.launcher.extra.LABEL"
  }
}
