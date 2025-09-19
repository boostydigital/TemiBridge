package com.spatium.temibridge.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class IntentEntryActivity : Activity() {

    private val robot by lazy { Robot.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
        finish()
    }

    private fun handleIntent(i: Intent) {
        // Deep links: mytemi://go?place=Lobby | mytemi://say?text=Hola
        val data: Uri? = i.data
        if (data != null && data.scheme == "mytemi") {
            when (data.host) {
                "go" -> data.getQueryParameter("place")?.let { place ->
                    if (place.isNotBlank()) robot.goTo(place)
                }
                "say" -> data.getQueryParameter("text")?.let { text ->
                    if (text.isNotBlank()) robot.speak(TtsRequest.create(text, false))
                }
                "tour" -> {
                    val name = data.getQueryParameter("name")?.trim().orEmpty()
                    val tourId = data.getQueryParameter("tourId")?.trim().orEmpty()
                    startTour(name.ifBlank { tourId })
                }
            }
            return
        }

        // Intents explícitos
        when (i.action) {
            "com.spatium.temibridge.ACTION_GO_TO" -> {
                val place = i.getStringExtra("place").orEmpty()
                if (place.isNotBlank()) robot.goTo(place)
            }
            "com.spatium.temibridge.ACTION_SAY" -> {
                val text = i.getStringExtra("text").orEmpty()
                if (text.isNotBlank()) robot.speak(TtsRequest.create(text, false))
            }
            "com.spatium.temibridge.ACTION_FOLLOW_ME" -> robot.beWithMe()
            "com.spatium.temibridge.ACTION_STOP" -> robot.stopMovement()
            "com.spatium.temibridge.ACTION_HEAD_TILT" -> {
                val angle = i.getIntExtra("angle", 0) // aprox. -25..25
                robot.tiltAngle(angle)
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
        // Dispara el NLU por defecto con el identificador del tour para que el Launcher lo resuelva.
        if (identifier.isNotBlank()) {
            robot.startDefaultNlu(identifier)
            robot.speak(TtsRequest.create("Iniciando tour $identifier", false))
        }
    }
}
