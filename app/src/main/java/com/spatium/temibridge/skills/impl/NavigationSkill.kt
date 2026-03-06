package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para navegar a una ubicación guardada en el mapa del robot.
 *
 * Parámetros:
 *  - place (String, requerido): nombre del waypoint destino
 *  - onArrival (() -> Unit, opcional): callback que se ejecuta al llegar
 *  - returnTo (String, opcional): ubicación a la que regresar después de llegar
 *  - returnDelayMs (Long, opcional): milisegundos de espera antes de regresar (default 10_000)
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "navigation", mapOf("place" to "Sala_Reuniones"))
 */
class NavigationSkill : BaseTemiSkill(
    skillId = "navigation",
    skillName = "Navigation",
    description = "Navega a waypoints guardados en el mapa del robot Temi",
    category = SkillCategory.NAVIGATION
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val place = params["place"] as? String
        if (place.isNullOrBlank()) {
            return SkillResult.Error("Parámetro requerido ausente: 'place'")
        }

        val onArrival = params["onArrival"] as? (() -> Unit)
        val returnTo = params["returnTo"] as? String
        val returnDelayMs = (params["returnDelayMs"] as? Number)?.toLong() ?: 10_000L

        logInfo("Navegando a: '$place'" +
            (if (returnTo != null) " (regresar a '$returnTo' en ${returnDelayMs}ms)" else ""))

        return withContext(Dispatchers.Main) {
            if (returnTo != null) {
                TemiController.setArrivalCallbackOnce {
                    logInfo("Llegada a '$place' detectada; regresando a '$returnTo' en ${returnDelayMs}ms")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onArrival?.invoke()
                        TemiController.goTo(returnTo)
                    }, returnDelayMs)
                }
            } else if (onArrival != null) {
                TemiController.setArrivalCallbackOnce {
                    logInfo("Llegada a '$place' detectada")
                    onArrival.invoke()
                }
            }

            TemiController.goTo(place)
            logInfo("Comando goTo('$place') enviado al robot")
            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
