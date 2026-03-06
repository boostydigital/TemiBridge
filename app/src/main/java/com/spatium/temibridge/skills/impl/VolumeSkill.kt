package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para controlar el volumen del robot Temi.
 *
 * Parámetros:
 *  - level (Int, requerido): nivel de volumen de 0 a 10
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "volume", mapOf("level" to 7))
 */
class VolumeSkill : BaseTemiSkill(
    skillId = "volume",
    skillName = "Volume",
    description = "Controla el nivel de volumen del robot Temi (0-10)",
    category = SkillCategory.SYSTEM
) {

    companion object {
        private const val VOLUME_MIN = 0
        private const val VOLUME_MAX = 10
    }

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val level = when (val raw = params["level"]) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return SkillResult.Error("Parámetro requerido ausente o inválido: 'level' (Int 0-10)")

        val clampedLevel = level.coerceIn(VOLUME_MIN, VOLUME_MAX)
        if (clampedLevel != level) {
            logWarn("Nivel de volumen $level fuera de rango [$VOLUME_MIN, $VOLUME_MAX], ajustado a $clampedLevel")
        }

        logInfo("Ajustando volumen a nivel=$clampedLevel")

        return withContext(Dispatchers.Main) {
            setVolume(clampedLevel)
        }
    }

    private fun setVolume(level: Int): SkillResult {
        return try {
            val cls = Class.forName("com.robotemi.sdk.Robot")
            val robot = cls.getMethod("getInstance").invoke(null)
                ?: return SkillResult.Error("Robot SDK no disponible")

            val setVolumeMethod = runCatching {
                robot.javaClass.getMethod("setVolume", Int::class.javaPrimitiveType)
            }.getOrNull()

            if (setVolumeMethod != null) {
                setVolumeMethod.invoke(robot, level)
                logInfo("setVolume($level) ejecutado correctamente")
                SkillResult.Success
            } else {
                logWarn("Método setVolume no encontrado en el SDK")
                SkillResult.Error("Método setVolume no disponible en esta versión del SDK")
            }
        } catch (t: Throwable) {
            logError("Error en setVolume: ${t.message}", t)
            SkillResult.Error("Error ajustando volumen: ${t.message}", t)
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
