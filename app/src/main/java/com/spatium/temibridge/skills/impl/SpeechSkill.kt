package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para síntesis de voz (TTS) usando el TTS nativo del robot Temi.
 *
 * Parámetros:
 *  - text (String, requerido): texto a sintetizar
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "speech", mapOf("text" to "Bienvenido al evento"))
 */
class SpeechSkill : BaseTemiSkill(
    skillId = "speech",
    skillName = "Speech",
    description = "Síntesis de voz usando TTS nativo del robot Temi",
    category = SkillCategory.SPEECH
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val text = params["text"] as? String
        if (text.isNullOrBlank()) {
            return SkillResult.Error("Parámetro requerido ausente: 'text'")
        }

        logInfo("Hablando texto (${text.length} chars)")

        return withContext(Dispatchers.Main) {
            TemiController.speak(text)
            logInfo("Comando speak enviado al robot")
            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
