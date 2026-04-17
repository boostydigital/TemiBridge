package com.spatium.deamon.db.temi.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.spatium.deamon.db.temi.R

/**
 * Activity fullscreen que muestra el anuncio durante el modo patrullaje.
 * Se mantiene visible durante TODO el recorrido del robot.
 */
class AnnouncementActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AnnouncementActivity"
        const val EXTRA_TEXTO = "extra_texto"
        const val EXTRA_IMAGEN_URL = "extra_imagen_url"
        const val EXTRA_ANUNCIO_ID = "extra_anuncio_id"
        const val ACTION_CLOSE = "com.spatium.deamon.db.temi.CLOSE_ANNOUNCEMENT"
    }

    private lateinit var imageView: ImageView
    private lateinit var textView: TextView
    private lateinit var indicatorView: TextView
    
    // Receiver para cerrar la activity cuando el anuncio expire
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Recibido broadcast de cierre")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen immersive - máxima prioridad para estar encima de UI de Temi
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        // Flags para estar encima de todo y mantener pantalla encendida
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        setContentView(R.layout.activity_announcement)
        
        imageView = findViewById(R.id.announcementImage)
        textView = findViewById(R.id.announcementText)
        indicatorView = findViewById(R.id.announcementIndicator)
        
        // Obtener datos del intent
        val texto = intent.getStringExtra(EXTRA_TEXTO) ?: ""
        val imagenUrl = intent.getStringExtra(EXTRA_IMAGEN_URL)
        val anuncioId = intent.getStringExtra(EXTRA_ANUNCIO_ID) ?: ""
        
        Log.d(TAG, "Mostrando anuncio: id=$anuncioId, texto=$texto, imagen=$imagenUrl")
        
        // Mostrar texto
        textView.text = texto
        
        // Cargar imagen si existe (fullscreen sin bordes)
        if (!imagenUrl.isNullOrBlank()) {
            imageView.visibility = View.VISIBLE
            imageView.load(imagenUrl) {
                crossfade(true)
                crossfade(300)
                listener(
                    onSuccess = { _, _ ->
                        Log.d(TAG, "✓ Imagen cargada exitosamente")
                    },
                    onError = { _, throwable ->
                        Log.e(TAG, "❌ Error cargando imagen: ${throwable.throwable.message}")
                        imageView.visibility = View.GONE
                    }
                )
            }
        } else {
            imageView.visibility = View.GONE
        }
        
        // Animación del indicador
        startIndicatorAnimation()
        
        // Registrar receiver para cierre
        registerReceiver(closeReceiver, IntentFilter(ACTION_CLOSE), Context.RECEIVER_NOT_EXPORTED)
    }
    
    private fun startIndicatorAnimation() {
        indicatorView.animate()
            .alpha(0.5f)
            .setDuration(800)
            .withEndAction {
                indicatorView.animate()
                    .alpha(1f)
                    .setDuration(800)
                    .withEndAction {
                        startIndicatorAnimation()
                    }
                    .start()
            }
            .start()
    }
    
    override fun onBackPressed() {
        // Deshabilitar botón back durante anuncio
        Log.d(TAG, "Back button deshabilitado durante anuncio")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {
            // Ignorar si ya fue desregistrado
        }
        Log.d(TAG, "AnnouncementActivity destruida")
    }
}
