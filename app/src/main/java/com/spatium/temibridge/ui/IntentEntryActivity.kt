package com.spatium.deamon.db.temi.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.spatium.deamon.db.temi.core.TemiController

class IntentEntryActivity : Activity() {

    // Ya no inicializamos directamente el SDK de Temi; usamos un controlador por reflexión.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    // Decodificador robusto (hasta 3 pasadas) similar al de MainActivity
    private fun decodeParam(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var prev: String = raw
        var curr: String
        repeat(3) {
            curr = try {
                java.net.URLDecoder.decode(prev, java.nio.charset.StandardCharsets.UTF_8.name())
            } catch (_: Throwable) {
                prev
            }
            if (curr == prev) return curr
            prev = curr
        }
        return prev
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(i: Intent) {
        // Deep links: mytemi://go?place=Lobby | mytemi://say?text=Hola
        val data: Uri? = i.data
        if (data != null && data.scheme == "mytemi") {
            when (data.host) {
                // Restaurar comportamiento: 'go' navega al lugar usando goTo(place)
                // y si recepcion=false, al llegar anuncia y luego va a "entrada" tras 10s
                "go" -> {
                    val place = decodeParam(data.getQueryParameter("place")).trim()
                    val recepcion = decodeParam(data.getQueryParameter("recepcion"))
                    val recBool = recepcion.equals("true", ignoreCase = true)
                    if (place.isNotBlank()) {
                        if (!recBool) {
                            TemiController.setArrivalCallbackOnce {
                                Handler(Looper.getMainLooper()).post {
                                    TemiController.speak("Hemos llegado a tu destino, tu anfitrión te atenderá. Gracias")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        TemiController.goTo("entrada")
                                    }, 10_000)
                                }
                            }
                        }
                        TemiController.goTo(place)
                        // Cerrar activity headless para no dejarla en el back stack
                        goHome()
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
                // Solicitar permiso de sequences explícitamente
                "sequence-permission" -> {
                    if (TemiController.isSequencePermissionGranted()) {
                        TemiController.speak("El permiso de secuencias ya está concedido")
                        Handler(Looper.getMainLooper()).postDelayed({ goHome() }, 2000)
                    } else {
                        val ok = TemiController.requestSequencePermission()
                        if (ok) {
                            TemiController.speak("Solicitando permiso de secuencias. Por favor, acepta en pantalla")
                            // Mantener esta activity en primer plano unos segundos para que el diálogo pueda mostrarse
                            Handler(Looper.getMainLooper()).postDelayed({
                                goHome()
                            }, 6000)
                        } else {
                            TemiController.speak("No pude solicitar el permiso de secuencias")
                            goHome()
                        }
                    }
                }
                // Listar sequences disponibles en el robot, gestionando permiso si falta
                "sequence-list" -> {
                    if (TemiController.isSequencePermissionGranted()) {
                        TemiController.logAndSpeakSequenceNames()
                        goHome()
                    } else {
                        val ok = TemiController.requestSequencePermission()
                        if (ok) {
                            TemiController.speak("Permiso de secuencias requerido. Acepta en pantalla y vuelve a intentarlo")
                            Handler(Looper.getMainLooper()).postDelayed({
                                goHome()
                            }, 6000)
                        } else {
                            TemiController.speak("No se pudo solicitar el permiso de secuencias")
                            goHome()
                        }
                    }
                }
                // Accion combinada: decir un saludo y luego ir a un lugar con un solo QR.
                // Ejemplo: mytemi://welcome?text=Hola%20David%2C%20bienvenido%20al%20Gastrobar&place=Gastrobar
                "welcome" -> {
                    val text = decodeParam(data.getQueryParameter("text")).trim()
                    val place = decodeParam(data.getQueryParameter("place")).trim()
                    val recepcion = decodeParam(data.getQueryParameter("recepcion"))
                    val recBool = recepcion.equals("true", ignoreCase = true)
                    if (text.isNotBlank()) {
                        TemiController.speak(text)
                    }
                    if (place.isNotBlank()) {
                        if (!recBool) {
                            TemiController.setArrivalCallbackOnce {
                                Handler(Looper.getMainLooper()).post {
                                    TemiController.speak("Hemos llegado a tu destino, tu anfitrión te atenderá. Gracias")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        TemiController.goTo("entrada")
                                    }, 10_000)
                                }
                            }
                        } else {
                            // Si es recepción, abrir KioskWebActivity a los 5s como en MainActivity
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    val url = "https://spatium-desk.lovable.app/pedidos-publicos?ubicacion=Recepcion"
                                    val intent = Intent(this, KioskWebActivity::class.java).apply {
                                        putExtra(KioskWebActivity.EXTRA_URL, url)
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    }
                                    startActivity(intent)
                                } catch (t: Throwable) {
                                    Log.w("TemiBridge", "Abrir KioskWebActivity fallo: ${t.message}")
                                }
                            }, 5_000)
                        }
                        TemiController.goTo(place)
                    }
                    goHome()
                }
                // Reproducir una sequence por nombre explícito (atajo): mytemi://sequence-play?name=Open_Space
                "sequence-play" -> {
                    val name = data.getQueryParameter("name")?.trim().orEmpty()
                    if (name.isNotBlank()) {
                        if (TemiController.isSequencePermissionGranted()) {
                            val ok = TemiController.playSequenceByName(name)
                            if (!ok) TemiController.speak("No encontré la secuencia $name")
                        } else {
                            val okReq = TemiController.requestSequencePermission(this)
                            val msg = if (okReq) "Permiso de secuencias requerido. Acepta en pantalla y vuelve a intentarlo" else "No se pudo solicitar el permiso de secuencias"
                            TemiController.speak(msg)
                        }
                    } else {
                        TemiController.speak("Falta el nombre de la secuencia")
                    }
                    goHome()
                }
                // Ejecutar una sequence por nombre explícito: mytemi://sequence?name=Open_Space
                "sequence" -> {
                    val name = data.getQueryParameter("name")?.trim().orEmpty()
                    if (name.isNotBlank()) {
                        val ok = TemiController.playSequenceByName(name)
                        if (!ok) TemiController.speak("No encontré la secuencia $name")
                    }
                    goHome()
                }
                // Controlar secuencia en reproducción: mytemi://sequence-control?action=next|pause|play|previous|stop
                "sequence-control" -> {
                    val action = data.getQueryParameter("action")?.trim().orEmpty()
                    if (action.isNotBlank()) {
                        val ok = TemiController.controlSequence(action)
                        if (!ok) TemiController.speak("No pude enviar comando de secuencia: $action")
                    }
                    goHome()
                }
                // Escort: solo reproduce el mensaje de greeting
                "escort" -> {
                    val greeting = decodeParam(data.getQueryParameter("greeting")).trim()
                    if (greeting.isNotBlank()) {
                        TemiController.speak(greeting)
                        Log.d("TemiBridge", "[ESCORT] Mensaje: $greeting")
                    } else {
                        TemiController.speak("Falta el mensaje de bienvenida")
                    }
                    goHome()
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
