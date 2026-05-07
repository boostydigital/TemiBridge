package com.spatium.deamon.db.temi.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spatium.deamon.db.temi.R

class KioskWebActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var urlLoaded = false

    private val autoClose = Runnable {
        Log.d(TAG, "Auto-close timer disparado, volviendo a MainActivity")
        returnToMain()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate llamado - intent: $intent")
        Log.d(TAG, "onCreate - extras: ${intent.extras}")

        setContentView(R.layout.activity_kiosk_web)

        // Pantalla completa y mantener encendido
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        webView = findViewById(R.id.webView)
        webView?.let { wv ->
            val ws: WebSettings = wv.settings
            ws.javaScriptEnabled = true
            ws.domStorageEnabled = true
            ws.loadWithOverviewMode = true
            ws.useWideViewPort = true
            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "WebView onPageStarted: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "WebView onPageFinished: $url")
                    urlLoaded = true
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.e(TAG, "WebView error: code=$errorCode, desc=$description, url=$failingUrl")
                }
            }

            loadUrlFromIntent(intent)
        }

        // Back button overlay handler: volver en el WebView o cerrar actividad
        findViewById<android.view.View>(R.id.btnBack)?.setOnClickListener {
            val wv = webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                returnToMain()
            }
        }

        // Programar cierre en 2 minutos (120_000 ms)
        handler.postDelayed(autoClose, 120_000)

        // Manejo de back usando OnBackPressedDispatcher (evita API deprecada)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        // Ignorar botón físico; se usa el botón visual de la UI
                    }
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent llamado - intent: $intent")
        Log.d(TAG, "onNewIntent - extras: ${intent.extras}")
        setIntent(intent)
        loadUrlFromIntent(intent)
        // Reiniciar timer de auto-close
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, 120_000)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy llamado")
        handler.removeCallbacks(autoClose)
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun loadUrlFromIntent(intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_URL)
        Log.d(TAG, "loadUrlFromIntent: EXTRA_URL=$url")

        if (!url.isNullOrBlank()) {
            Log.d(TAG, "Cargando URL en WebView: $url")
            urlLoaded = false
            webView?.loadUrl(url)
        } else {
            Log.w(TAG, "EXTRA_URL vacío o nulo; volviendo a MainActivity")
            returnToMain()
        }
    }

    private fun returnToMain() {
        Log.d(TAG, "returnToMain() llamado")
        try {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(mainIntent)
            finish()
        } catch (t: Throwable) {
            Log.e(TAG, "Error volviendo a MainActivity: ${t.message}", t)
            finish()
        }
    }

    companion object {
        private const val TAG = "KioskWeb"
        const val EXTRA_URL = "url"
    }
}
