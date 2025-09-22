package com.spatium.temibridge.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spatium.temibridge.R

class KioskWebActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    private val autoClose = Runnable {
        // Volver a la MainActivity tras 5 minutos
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // Mantener navegación dentro del WebView
                    return false
                }
            }

            val url = intent.getStringExtra(EXTRA_URL)
            if (!url.isNullOrBlank()) {
                wv.loadUrl(url)
            }
        }

        // Programar cierre en 5 minutos (300000 ms)
        handler.postDelayed(autoClose, 300_000)

        // Manejo de back usando OnBackPressedDispatcher (evita API deprecada)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    // Ignorar back para modo quiosco
                }
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoClose)
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    // Sin override de onBackPressed (API deprecada) — gestionado por OnBackPressedDispatcher

    companion object {
        const val EXTRA_URL = "url"
    }
}
