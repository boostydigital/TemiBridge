package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para síntesis de voz usando el TTS nativo del robot Temi.
 * Proporciona control directo sobre el sistema de TTS integrado del robot.
 *
 * Parámetros:
 *  - text (String, requerido): texto a sintetizar
 *  - language (String, opcional): código de idioma (ej: "es", "en", "fr") - depende del SDK Temi
 *  - speed (Float, opcional): velocidad de habla (0.5 a 2.0, default 1.0)
 *  - pitch (Float, opcional): tono de voz (0.5 a 2.0, default 1.0)
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "native_tts", mapOf(
 *     "text" to "Bienvenido al evento",
 *     "speed" to 1.2f,
 *     "pitch" to 1.0f
 *   ))
 *
 * Notas:
 *  - El TTS nativo de Temi es más rápido y confiable que APIs externas
 *  - No requiere conexión a internet
 *  - Los parámetros de speed y pitch pueden no ser soportados en todas las versiones del SDK
 */
class NativeTtsSkill : BaseTemiSkill(
    skillId = "native_tts",
    skillName = "Native TTS",
    description = "Síntesis de voz usando el TTS nativo integrado del robot Temi",
    category = SkillCategory.SPEECH
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val text = params["text"] as? String
        if (text.isNullOrBlank()) {
            return SkillResult.Error("Parámetro requerido ausente: 'text'")
        }

        val language = params["language"] as? String
        val speed = (params["speed"] as? Number)?.toFloat() ?: 1.0f
        val pitch = (params["pitch"] as? Number)?.toFloat() ?: 1.0f

        logInfo("Hablando texto (${text.length} chars) - language=$language speed=$speed pitch=$pitch")

        return withContext(Dispatchers.Main) {
            try {
                // Intentar usar parámetros avanzados si están disponibles
                val result = speakWithParameters(text, language, speed, pitch)
                if (result) {
                    logInfo("TTS nativo ejecutado correctamente")
                    SkillResult.Success
                } else {
                    logWarn("TTS nativo retornó false, usando fallback simple")
                    TemiController.speak(text)
                    SkillResult.Success
                }
            } catch (t: Throwable) {
                logError("Error en TTS nativo: ${t.message}", t)
                // Fallback: intentar con speak simple
                try {
                    TemiController.speak(text)
                    SkillResult.Success
                } catch (e: Throwable) {
                    SkillResult.Error("Error en TTS nativo: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Intenta usar parámetros avanzados de TTS si el SDK los soporta.
     * Fallback a speak() simple si no están disponibles.
     */
    private fun speakWithParameters(
        text: String,
        language: String?,
        speed: Float,
        pitch: Float
    ): Boolean {
        return try {
            val cls = Class.forName("com.robotemi.sdk.Robot")
            val robot = cls.getMethod("getInstance").invoke(null)
                ?: return false

            // Intentar método con TtsRequest que soporta parámetros
            val ttsRequestCls = Class.forName("com.robotemi.sdk.TtsRequest")

            // Intentar builder pattern si existe
            val builderMethod = runCatching {
                ttsRequestCls.getMethod("create", String::class.java, Boolean::class.javaPrimitiveType)
            }.getOrNull()

            if (builderMethod != null) {
                val ttsRequest = builderMethod.invoke(null, text, false)
                
                // Intentar establecer parámetros avanzados
                runCatching {
                    ttsRequest?.javaClass?.getMethod("setSpeed", Float::class.javaPrimitiveType)
                        ?.invoke(ttsRequest, speed)
                }
                
                runCatching {
                    ttsRequest?.javaClass?.getMethod("setPitch", Float::class.javaPrimitiveType)
                        ?.invoke(ttsRequest, pitch)
                }

                if (language != null) {
                    runCatching {
                        ttsRequest?.javaClass?.getMethod("setLanguage", String::class.java)
                            ?.invoke(ttsRequest, language)
                    }
                }

                // Ejecutar speak con el request
                val speakMethod = robot.javaClass.getMethod("speak", ttsRequestCls)
                speakMethod.invoke(robot, ttsRequest)
                true
            } else {
                // Fallback: usar speak simple
                logWarn("TtsRequest builder no disponible, usando speak simple")
                TemiController.speak(text)
                true
            }
        } catch (t: Throwable) {
            logWarn("speakWithParameters falló: ${t.message}, usando fallback")
            false
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
