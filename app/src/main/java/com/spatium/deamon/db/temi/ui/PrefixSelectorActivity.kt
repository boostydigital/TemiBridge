package com.spatium.deamon.db.temi.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.robotemi.sdk.Robot
import com.spatium.deamon.db.temi.R

class PrefixSelectorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PrefixSelectorActivity"
        const val EXTRA_SELECTED_PREFIX = "selected_prefix"
    }

    private var robot: Robot? = null
    private val prefixGroups = mutableMapOf<String, List<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prefix_selector)
        Log.d(TAG, "[LIFECYCLE] PrefixSelectorActivity.onCreate")

        setupFullscreen()

        robot = try {
            Robot.getInstance()
        } catch (t: Throwable) {
            Log.e(TAG, "[ROBOT] Error obteniendo instancia: ${t.message}")
            null
        }

        setupButtons()
        loadAndDisplayPrefixes()
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
        findViewById<FrameLayout>(R.id.btnSelectorBack).setOnClickListener {
            Log.d(TAG, "[UI] Botón volver tocado")
            finish()
        }
    }

    private fun loadAndDisplayPrefixes() {
        val r = robot ?: run {
            updateStatusText("⚠️ Robot no disponible")
            return
        }

        try {
            val allLocations = r.locations
            Log.d(TAG, "[LOCATIONS] Total puntos: ${allLocations.size} → $allLocations")

            if (allLocations.isEmpty()) {
                updateStatusText("⚠️ No hay puntos guardados en el mapa")
                return
            }

            // Extraer prefijos automáticamente (sin caracteres especiales)
            // Estrategia: buscar patrones comunes en nombres
            // Ej: spatiumfotoentrada, spatiumfotocentro → prefijo "spatiumfoto"
            //     hackathonentrada, hackathonsala → prefijo "hackathon"
            prefixGroups.clear()

            // Detectar prefijos por longitud común y patrones
            val prefixMap = mutableMapOf<String, MutableList<String>>()
            allLocations.forEach { location ->
                // Intentar encontrar prefijo común con otros nombres
                var foundPrefix: String? = null
                for (other in allLocations) {
                    if (other != location) {
                        val commonPrefix = location.commonPrefixWith(other)
                        if (commonPrefix.length >= 4) { // mínimo 4 caracteres
                            foundPrefix = commonPrefix
                            break
                        }
                    }
                }

                if (foundPrefix != null && foundPrefix.isNotEmpty()) {
                    prefixMap.getOrPut(foundPrefix) { mutableListOf() }.add(location)
                }
            }

            prefixGroups.putAll(prefixMap)

            // Filtrar solo grupos con 2+ puntos
            val validGroups = prefixGroups.filter { it.value.size >= 2 }
            Log.d(TAG, "[PREFIXES] Grupos válidos: ${validGroups.keys}")

            if (validGroups.isEmpty()) {
                updateStatusText(
                    "⚠️ No hay eventos configurados.\n\n" +
                        "Necesitas al menos 2 puntos por evento.\n" +
                        "Ejemplo: spatiumfotoentrada, spatiumfotocentro",
                )
                return
            }

            updateStatusText("Selecciona un evento:")
            displayPrefixButtons(validGroups)
        } catch (t: Throwable) {
            Log.e(TAG, "[LOCATIONS] Error cargando ubicaciones: ${t.message}", t)
            updateStatusText("⚠️ Error cargando ubicaciones: ${t.message}")
        }
    }

    private fun displayPrefixButtons(groups: Map<String, List<String>>) {
        val container = findViewById<LinearLayout>(R.id.prefixButtonsContainer)
        container.removeAllViews()

        groups.forEach { (prefix, locations) ->
            val button = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    120,
                ).apply { setMargins(0, 0, 0, 16) }
                background = resources.getDrawable(R.drawable.bg_glass_panel_rounded, null)
                isClickable = true
                isFocusable = true
            }

            val content = LinearLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(32, 16, 32, 16)
            }

            val titleText = TextView(this).apply {
                text = prefix.uppercase()
                textSize = 24f
                setTextColor(resources.getColor(android.R.color.white, null))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val countText = TextView(this).apply {
                text = "${locations.size} puntos"
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 4 }
            }

            content.addView(titleText)
            content.addView(countText)
            button.addView(content)

            button.setOnClickListener {
                Log.d(TAG, "[SELECT] Prefijo seleccionado: $prefix (${locations.size} puntos)")
                launchFotosActivity(prefix)
            }

            container.addView(button)
        }
    }

    private fun launchFotosActivity(prefix: String) {
        val intent = Intent(this, FotosActivity::class.java).apply {
            putExtra(FotosActivity.EXTRA_LOCATION_PREFIX, prefix)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun updateStatusText(text: String) {
        runOnUiThread {
            try {
                findViewById<TextView>(R.id.tvSelectorStatus)?.text = text
            } catch (t: Throwable) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "[LIFECYCLE] PrefixSelectorActivity destruida")
    }
}
