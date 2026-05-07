package com.spatium.deamon.db.temi.core

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.spatium.deamon.db.temi.BuildConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Google Cloud Text-to-Speech con voz natural usando la API key
 * Usa voces Neural2 o WaveNet para máxima naturalidad
 */
object GoogleTTS {
    private const val TAG = "GoogleTTS"

    // API key leída desde BuildConfig (inyectada por Gradle)
    private val apiKey: String
        get() = BuildConfig.GOOGLE_TTS_API_KEY

    // Construye el endpoint de Google Cloud TTS usando la API key actual
    private fun buildTtsUrl(): String =
        "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"

    private val client = OkHttpClient.Builder()
        // Timeouts más agresivos: si la red está lenta, preferimos fallback rápido al TTS nativo
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Habla el texto usando Google Cloud TTS con voz natural
     * @param context Contexto de Android para guardar archivo temporal
     * @param text Texto a sintetizar
     * @param onComplete Callback cuando termina de hablar (opcional)
     */
    fun speak(context: Context, text: String, onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        scope.launch {
            try {
                if (apiKey.isBlank()) {
                    Log.e(TAG, "GOOGLE_TTS_API_KEY está vacío. Usando TTS nativo de Temi.")
                    withContext(Dispatchers.Main) {
                        TemiController.speak(text)
                        onComplete?.invoke()
                    }
                    return@launch
                }

                Log.d(TAG, "Sintetizando (Google TTS): $text")

                // Construir request JSON para voz natural en español
                val jsonBody = JSONObject().apply {
                    put("input", JSONObject().put("text", text))
                    put(
                        "voice",
                        JSONObject().apply {
                            put("languageCode", "es-US")
                            put("name", "es-US-Neural2-B") // Voz masculina neural muy natural
                        },
                    )
                    put(
                        "audioConfig",
                        JSONObject().apply {
                            put("audioEncoding", "MP3")
                            put("speakingRate", 1.0) // Velocidad normal
                            put("pitch", 0.0) // Tono normal
                            put("volumeGainDb", 2.0) // Un poco más alto
                        },
                    )
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(buildTtsUrl())
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "Error TTS API: ${response.code} - $errorBody")
                    // Fallback al TTS del Temi
                    withContext(Dispatchers.Main) {
                        TemiController.speak(text)
                        onComplete?.invoke()
                    }
                    return@launch
                }

                val responseBody = response.body?.string() ?: ""
                val json = JSONObject(responseBody)
                val audioContent = json.getString("audioContent")

                // Decodificar base64 a bytes
                val audioBytes = Base64.decode(audioContent, Base64.DEFAULT)

                // Guardar en archivo temporal
                val tempFile = File(context.cacheDir, "tts_audio_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { it.write(audioBytes) }

                Log.d(TAG, "Audio guardado: ${tempFile.absolutePath} (${audioBytes.size} bytes)")

                // Reproducir en hilo principal
                withContext(Dispatchers.Main) {
                    playAudio(tempFile, onComplete)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en TTS: ${e.message}", e)
                // Fallback al TTS del Temi
                withContext(Dispatchers.Main) {
                    TemiController.speak(text)
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun playAudio(file: File, onComplete: (() -> Unit)?) {
        try {
            // Liberar reproductor anterior
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "Audio completado")
                    it.release()
                    file.delete() // Limpiar archivo temporal
                    onComplete?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Error MediaPlayer: what=$what extra=$extra")
                    file.delete()
                    onComplete?.invoke()
                    true
                }
                prepare()
                start()
                Log.d(TAG, "Reproduciendo audio...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reproduciendo audio: ${e.message}", e)
            file.delete()
            onComplete?.invoke()
        }
    }

    /**
     * Detiene la reproducción actual
     */
    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error deteniendo audio: ${e.message}")
        }
    }

    /**
     * Limpia recursos
     */
    fun release() {
        stop()
        scope.cancel()
    }
}
