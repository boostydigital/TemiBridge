package com.spatium.deamon.db.temi.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spatium.deamon.db.temi.R

class OrderSuccessActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCT_NAME = "product_name"
        private const val TAG = "OrderSuccessActivity"
        private const val PREFS_NAME = "menu_prefs"
        private const val KEY_FAREWELL_SEQUENCE_ID = "farewell_sequence_id"
        private const val DEFAULT_FAREWELL_SEQUENCE_ID = "694063d5bd16eddf28b772d8"
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_success)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupFullscreen()

        val productName = intent.getStringExtra(EXTRA_PRODUCT_NAME) ?: "tu pedido"
        findViewById<TextView>(R.id.tvProductName).text = productName

        // Aplicar animación de entrada al icono de éxito con ObjectAnimator
        val successCircle = findViewById<FrameLayout>(R.id.successCircle)
        successCircle.scaleX = 0f
        successCircle.scaleY = 0f
        successCircle.alpha = 0f
        successCircle.rotation = -45f

        val scaleXAnimator = ObjectAnimator.ofFloat(successCircle, "scaleX", 0f, 1.2f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(successCircle, "scaleY", 0f, 1.2f, 1f)
        val alphaAnimator = ObjectAnimator.ofFloat(successCircle, "alpha", 0f, 1f)
        val rotationAnimator = ObjectAnimator.ofFloat(successCircle, "rotation", -45f, 0f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator, rotationAnimator)
            duration = 1000
        }
        animatorSet.start()
        Log.d(TAG, "Animación de éxito iniciada")

        // Mostrar por 3 segundos y luego ejecutar secuencia
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "3 segundos transcurridos, ejecutando secuencia...")
            val farewellSequenceId = prefs.getString(KEY_FAREWELL_SEQUENCE_ID, DEFAULT_FAREWELL_SEQUENCE_ID)
                ?: DEFAULT_FAREWELL_SEQUENCE_ID
            executeFarewellSequence(farewellSequenceId)

            // Volver a MainActivity después de ejecutar la secuencia
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "5 segundos transcurridos, volviendo a MainActivity...")
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            }, 5000) // 5 segundos para que la secuencia se ejecute completamente
        }, 3000) // 3 segundos

        // Botones (por si el usuario quiere interactuar antes de los 3 segundos)
        findViewById<Button>(R.id.btnBackHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.btnNewOrder).setOnClickListener {
            val intent = Intent(this, MenuActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun executeFarewellSequence(sequenceId: String) {
        Log.d(TAG, "=== EJECUTANDO SECUENCIA DE DESPEDIDA === ID: $sequenceId")
        try {
            // Verificar si tenemos permiso de secuencias
            if (!com.spatium.deamon.db.temi.core.TemiController.isSequencePermissionGranted()) {
                Log.w(TAG, "Permiso de secuencias no concedido, solicitando...")
                val granted = com.spatium.deamon.db.temi.core.TemiController.requestSequencePermission(this)
                if (!granted) {
                    Log.e(TAG, "ERROR: No se pudo obtener permiso de secuencias")
                    return
                }
                Log.d(TAG, "Permiso de secuencias otorgado")
            }

            val success = com.spatium.deamon.db.temi.core.TemiController.playSequenceById(sequenceId)
            Log.d(TAG, "playSequenceById resultado: $success")
            if (!success) Log.e(TAG, "ERROR: No se pudo ejecutar la secuencia $sequenceId")
        } catch (e: Exception) {
            Log.e(TAG, "Excepción ejecutando secuencia: ${e.message}", e)
        }
    }

    private fun returnToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        returnToMain()
        super.onBackPressed()
    }
}
