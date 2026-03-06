package com.spatium.deamon.db.temi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.spatium.deamon.db.temi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RatingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RatingActivity"
        const val PREFS_NAME = "rating_prefs"
        const val KEY_EVENT_NAME = "rating_event_name"
        const val KEY_WEBHOOK_URL = "rating_webhook_url"
        private const val DEFAULT_EVENT_NAME = "EVENTO SPATIUM"
        private const val AUTO_RETURN_DELAY = 8000L
    }

    private lateinit var webView: WebView
    private var eventName = DEFAULT_EVENT_NAME
    private var webhookUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rating)
        Log.d(TAG, "[LIFECYCLE] RatingActivity.onCreate")

        setupFullscreen()
        loadPreferences()
        setupWebView()
        loadRatingPage()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        eventName = prefs.getString(KEY_EVENT_NAME, DEFAULT_EVENT_NAME) ?: DEFAULT_EVENT_NAME
        webhookUrl = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        Log.d(TAG, "[CONFIG] eventName=$eventName, webhookUrl=$webhookUrl")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.ratingWebView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.addJavascriptInterface(RatingBridge(), "Android")
        webView.webViewClient = WebViewClient()
    }

    private fun loadRatingPage() {
        try {
            val html = assets.open("rating.html").bufferedReader().readText()
                .replace("{{EVENT_NAME}}", eventName)
            webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
            Log.d(TAG, "[UI] Rating page loaded with event: $eventName")
        } catch (e: Exception) {
            Log.e(TAG, "[UI] Error loading rating page: ${e.message}")
            finish()
        }
    }

    private fun loadThankYouPage() {
        try {
            val html = assets.open("rating_thanks.html").bufferedReader().readText()
            runOnUiThread {
                webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
            }
            Log.d(TAG, "[UI] Thank you page loaded")

            // Auto-return after delay
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "[AUTO] Auto-return after thank you")
                finish()
            }, AUTO_RETURN_DELAY)
        } catch (e: Exception) {
            Log.e(TAG, "[UI] Error loading thank you page: ${e.message}")
            finish()
        }
    }

    private fun sendRatingAsync(rating: Int) {
        if (webhookUrl.isBlank()) {
            Log.w(TAG, "[WEBHOOK] No webhook URL configured - skipping send")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedEvent = java.net.URLEncoder.encode(eventName, "UTF-8")
                val separator = if (webhookUrl.contains("?")) "&" else "?"
                val fullUrl = "${webhookUrl}${separator}rating=$rating&event=$encodedEvent"

                Log.d(TAG, "[WEBHOOK] Sending rating=$rating to: $fullUrl")
                val connection = java.net.URL(fullUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val responseCode = connection.responseCode
                Log.d(TAG, "[WEBHOOK] Response: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "[WEBHOOK] Error sending rating: ${e.message}")
            }
        }
    }

    inner class RatingBridge {
        @JavascriptInterface
        fun submitRating(rating: Int) {
            Log.d(TAG, "[BRIDGE] Rating submitted: $rating")
            sendRatingAsync(rating)
            loadThankYouPage()
        }

        @JavascriptInterface
        fun goBack() {
            Log.d(TAG, "[BRIDGE] Back pressed")
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun skip() {
            Log.d(TAG, "[BRIDGE] Skip pressed")
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun goHome() {
            Log.d(TAG, "[BRIDGE] Home pressed")
            runOnUiThread { finish() }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
        Log.d(TAG, "[LIFECYCLE] RatingActivity destroyed")
    }
}
