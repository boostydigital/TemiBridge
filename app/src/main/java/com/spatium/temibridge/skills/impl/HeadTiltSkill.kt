package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import android.util.Log
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para controlar la inclinación de la cabeza del robot Temi.
 *
 * Parámetros:
 *  - angle (Int, requerido): ángulo de inclinación en grados
 *    Rango típico del SDK Temi: -25 (abajo) a 55 (arriba)
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "head_tilt", mapOf("angle" to 20))
 */
class HeadTiltSkill : BaseTemiSkill(
    skillId = "head_tilt",
    skillName = "Head Tilt",
    description = "Controla el ángulo de inclinación de la cabeza del robot Temi",
    category = SkillCategory.SYSTEM
) {

    companion object {
        private const val ANGLE_MIN = -25
        private const val ANGLE_MAX = 55
    }

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val angle = when (val raw = params["angle"]) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return SkillResult.Error("Parámetro requerido ausente o inválido: 'angle' (Int)")

        val clampedAngle = angle.coerceIn(ANGLE_MIN, ANGLE_MAX)
        if (clampedAngle != angle) {
            logWarn("Ángulo $angle fuera de rango [$ANGLE_MIN, $ANGLE_MAX], ajustado a $clampedAngle")
        }

        logInfo("Inclinando cabeza a ángulo=$clampedAngle grados")

        return withContext(Dispatchers.Main) {
            tiltHead(clampedAngle)
        }
    }

    private fun tiltHead(angle: Int): SkillResult {
        return try {
            val cls = Class.forName("com.robotemi.sdk.Robot")
            val robot = cls.getMethod("getInstance").invoke(null)
                ?: return SkillResult.Error("Robot SDK no disponible")

            val tiltMethod = runCatching {
                robot.javaClass.getMethod("tiltAngle", Int::class.javaPrimitiveType)
            }.getOrNull() ?: runCatching {
                robot.javaClass.getMethod("tiltAngle", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            }.getOrNull()

            if (tiltMethod != null) {
                if (tiltMethod.parameterCount == 2) {
                    tiltMethod.invoke(robot, angle, 1.0f)
                } else {
                    tiltMethod.invoke(robot, angle)
                }
                logInfo("tiltAngle($angle) ejecutado correctamente")
                SkillResult.Success
            } else {
                logWarn("Método tiltAngle no encontrado en el SDK")
                SkillResult.Error("Método tiltAngle no disponible en esta versión del SDK")
            }
        } catch (t: Throwable) {
            logError("Error en tiltHead: ${t.message}", t)
            SkillResult.Error("Error inclinando cabeza: ${t.message}", t)
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
