package com.spatium.deamon.db.temi.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.robotemi.sdk.Robot
import com.spatium.deamon.db.temi.R

class MapSelectorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MapSelectorActivity"
        const val EXTRA_SELECTED_LOCATIONS = "selected_locations"
    }

    private var robot: Robot? = null
    private val selectedLocations = mutableSetOf<String>()
    private val locationCheckboxes = mutableMapOf<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_selector)
        Log.d(TAG, "[LIFECYCLE] MapSelectorActivity.onCreate")

        setupFullscreen()

        robot = try { Robot.getInstance() } catch (t: Throwable) {
            Log.e(TAG, "[ROBOT] Error obteniendo instancia: ${t.message}")
            null
        }

        setupButtons()
        loadAndDisplayLocations()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupButtons() {
        findViewById<FrameLayout>(R.id.btnMapBack).setOnClickListener {
            Log.d(TAG, "[UI] Botón volver tocado")
            finish()
        }

        findViewById<FrameLayout>(R.id.btnMapStart).setOnClickListener {
            Log.d(TAG, "[UI] Botón iniciar tocado")
            startWithSelectedLocations()
        }

        findViewById<FrameLayout>(R.id.btnMapSelectAll).setOnClickListener {
            Log.d(TAG, "[UI] Botón seleccionar todo tocado")
            selectAllLocations()
        }

        findViewById<FrameLayout>(R.id.btnMapClearAll).setOnClickListener {
            Log.d(TAG, "[UI] Botón limpiar todo tocado")
            clearAllLocations()
        }
    }

    private fun loadAndDisplayLocations() {
        val r = robot ?: run {
            updateStatusText("⚠️ Robot no disponible")
            return
        }

        try {
            val allLocations = r.locations
            Log.d(TAG, "[LOCATIONS] Total puntos en mapa: ${allLocations.size}")

            if (allLocations.isEmpty()) {
                updateStatusText("⚠️ No hay ubicaciones guardadas en el mapa del robot")
                return
            }

            Log.d(TAG, "[LOCATIONS] Ubicaciones: $allLocations")
            updateStatusText("Selecciona las ubicaciones para deambular:")
            displayLocationCheckboxes(allLocations.sorted())

        } catch (t: Throwable) {
            Log.e(TAG, "[LOCATIONS] Error cargando ubicaciones: ${t.message}", t)
            updateStatusText("⚠️ Error cargando ubicaciones: ${t.message}")
        }
    }

    private fun displayLocationCheckboxes(locations: List<String>) {
        val container = findViewById<LinearLayout>(R.id.locationsCheckboxContainer)
        container.removeAllViews()
        locationCheckboxes.clear()

        locations.forEach { location ->
            val checkboxContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    80
                ).apply { setMargins(0, 0, 0, 8) }
                background = resources.getDrawable(R.drawable.bg_glass_panel_rounded, null)
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(24, 0, 24, 0)
                isClickable = true
                isFocusable = true
            }

            val checkbox = CheckBox(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isChecked = false
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedLocations.add(location)
                        Log.d(TAG, "[SELECT] Seleccionado: $location")
                    } else {
                        selectedLocations.remove(location)
                        Log.d(TAG, "[DESELECT] Deseleccionado: $location")
                    }
                    updateCounterText()
                }
            }

            val textView = TextView(this).apply {
                text = location
                textSize = 18f
                setTextColor(resources.getColor(android.R.color.white, null))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = 16 }
            }

            checkboxContainer.addView(checkbox)
            checkboxContainer.addView(textView)

            // Hacer clickeable todo el contenedor
            checkboxContainer.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
            }

            container.addView(checkboxContainer)
            locationCheckboxes[location] = checkbox
        }

        updateCounterText()
    }

    private fun updateCounterText() {
        val count = selectedLocations.size
        try {
            findViewById<TextView>(R.id.tvMapCounter)?.text = 
                "Seleccionadas: $count ubicaciones"
        } catch (t: Throwable) { }
    }

    private fun selectAllLocations() {
        locationCheckboxes.forEach { (_, checkbox) ->
            checkbox.isChecked = true
        }
        Log.d(TAG, "[SELECT] Todas las ubicaciones seleccionadas")
    }

    private fun clearAllLocations() {
        locationCheckboxes.forEach { (_, checkbox) ->
            checkbox.isChecked = false
        }
        selectedLocations.clear()
        updateCounterText()
        Log.d(TAG, "[CLEAR] Todas las ubicaciones deseleccionadas")
    }

    private fun startWithSelectedLocations() {
        if (selectedLocations.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos una ubicación", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "[START] Sin ubicaciones seleccionadas")
            return
        }

        Log.d(TAG, "[START] Iniciando con ${selectedLocations.size} ubicaciones: $selectedLocations")

        val intent = Intent(this, SelfieHunterActivity::class.java).apply {
            putStringArrayListExtra(
                SelfieHunterActivity.EXTRA_SELECTED_LOCATIONS,
                ArrayList(selectedLocations.toList())
            )
            // No usar CLEAR_TOP para mantener el stack de actividades intacto
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        Log.d(TAG, "[START] ✅ Intent creado - Component: ${intent.component}")
        Log.d(TAG, "[START] ✅ Intent extras: ${intent.extras}")
        Log.d(TAG, "[START] 🚀 Llamando a startActivity(SelfieHunterActivity)")
        startActivity(intent)
        Log.d(TAG, "[START] ✅ startActivity ejecutado")
        // No hacer finish() aquí para mantener el stack
    }

    private fun updateStatusText(text: String) {
        runOnUiThread {
            try {
                findViewById<TextView>(R.id.tvMapStatus)?.text = text
            } catch (t: Throwable) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "[LIFECYCLE] MapSelectorActivity destruida")
    }
}
