package com.spatium.temibridge.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import com.spatium.temibridge.core.TemiController

class IntentEntryActivity : Activity() {

    // Ya no inicializamos directamente el SDK de Temi; usamos un controlador por reflexión.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        goHome()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
        goHome()
    }

    private fun handleIntent(i: Intent) {
        // Deep links: mytemi://go?place=Lobby | mytemi://say?text=Hola
        val data: Uri? = i.data
        if (data != null && data.scheme == "mytemi") {
            when (data.host) {
                // Restaurar comportamiento: 'go' navega al lugar usando goTo(place)
                "go" -> data.getQueryParameter("place")?.let { place ->
                    if (place.isNotBlank()) {
                        TemiController.goTo(place)
                    }
                }
                "say" -> data.getQueryParameter("text")?.let { text ->
                    if (text.isNotBlank()) TemiController.speak(text)
                }
                "tour" -> {
                    val name = data.getQueryParameter("name")?.trim().orEmpty()
                    val tourId = data.getQueryParameter("tourId")?.trim().orEmpty()
                    startTour(name.ifBlank { tourId })
                }
                // Accion combinada: decir un saludo y luego ir a un lugar con un solo QR.
                // Ejemplo: mytemi://welcome?text=Hola%20David%2C%20bienvenido%20al%20Gastrobar&place=Gastrobar
                "welcome" -> {
                    val text = data.getQueryParameter("text")?.trim().orEmpty()
                    val place = data.getQueryParameter("place")?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        TemiController.speak(text)
                    }
                    if (place.isNotBlank()) {
                        TemiController.playSequenceByName(place)
                    }
                }
                // Ejecutar una sequence por nombre explícito: mytemi://sequence?name=Open_Space
                "sequence" -> {
                    val name = data.getQueryParameter("name")?.trim().orEmpty()
                    if (name.isNotBlank()) {
                        val ok = TemiController.playSequenceByName(name)
                        if (!ok) TemiController.speak("No encontré la secuencia $name")
                    }
                }
                // Solicitar permiso de Sequence al robot: mytemi://sequence-permission
                "sequence-permission" -> {
                    val granted = TemiController.requestSequencePermission()
                    if (granted) TemiController.speak("Permiso de secuencias solicitado")
                }
                // Listar secuencias visibles en el robot: mytemi://sequence-list
                "sequence-list" -> {
                    val names = TemiController.listSequenceNames()
                    if (names.isEmpty()) {
                        TemiController.speak("No hay secuencias disponibles o permiso no concedido")
                    } else {
                        val joined = names.joinToString(", ")
                        android.util.Log.d("TemiBridge", "Sequences: $joined")
                        val say = if (joined.length > 120) names.take(5).joinToString(", ") else joined
                        TemiController.speak("Secuencias disponibles: $say")
                    }
                }
                // Controlar secuencia en reproducción: mytemi://sequence-control?action=next|pause|play|previous|stop
                "sequence-control" -> {
                    val action = data.getQueryParameter("action")?.trim().orEmpty()
                    if (action.isNotBlank()) {
                        val ok = TemiController.controlSequence(action)
                        if (!ok) TemiController.speak("No pude enviar comando de secuencia: $action")
                    }
                }
            }
            return
        }

        // Intents explícitos
        when (i.action) {
            "com.spatium.temibridge.ACTION_GO_TO" -> {
                val place = i.getStringExtra("place").orEmpty()
                if (place.isNotBlank()) TemiController.goTo(place)
            }
            "com.spatium.temibridge.ACTION_SAY" -> {
                val text = i.getStringExtra("text").orEmpty()
                if (text.isNotBlank()) TemiController.speak(text)
            }
            // Estas acciones requieren métodos adicionales: si se necesitan, podemos añadirlos al TemiController.
            // "com.spatium.temibridge.ACTION_FOLLOW_ME" ->
            // "com.spatium.temibridge.ACTION_STOP" ->
            "com.spatium.temibridge.ACTION_HEAD_TILT" -> {
                val angle = i.getIntExtra("angle", 0) // aprox. -25..25
                // Método no implementado en TemiController por simplicidad en emulador.
            }
            "com.spatium.temibridge.ACTION_VOLUME" -> {
                val level = i.getIntExtra("level", 5) // 0..10
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val clamped = level.coerceIn(0, 10)
                val target = (clamped * max) / 10
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
            "com.spatium.temibridge.ACTION_TOUR_START" -> {
                val name = i.getStringExtra("name").orEmpty().trim()
                val tourId = i.getStringExtra("tourId").orEmpty().trim()
                startTour(name.ifBlank { tourId })
            }
        }
    }

    private fun startTour(identifier: String) {
        // Intenta iniciar Tour de Temi Center por nombre (o id). Si no, fallback al NLU.
        if (identifier.isNotBlank()) {
            val started = TemiController.playTourByName(identifier)
                || TemiController.playTourById(identifier)
            if (started) {
                TemiController.speak("Iniciando tour $identifier")
            } else {
                // Fallback para compatibilidad
                TemiController.startDefaultNlu(identifier)
                TemiController.speak("Iniciando tour $identifier")
            }
        }
    }

    private fun goHome() {
        // Lleva/trae al frente la MainActivity para que la app siga abierta.
        val home = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(home)
        // Cerramos solo esta activity "puente" (transparente) para no contaminar el back stack.
        finish()
    }
}
