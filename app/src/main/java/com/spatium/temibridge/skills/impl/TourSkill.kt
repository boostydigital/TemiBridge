package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para iniciar tours predefinidos del robot Temi.
 *
 * Parámetros:
 *  - tourId (String): ID del tour (usa esto si se conoce el ID)
 *  - tourName (String): nombre del tour (alternativa al ID)
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "tour", mapOf("tourName" to "Tour Oficina"))
 */
class TourSkill :
    BaseTemiSkill(
        skillId = "tour",
        skillName = "Tour",
        description = "Inicia tours predefinidos del robot Temi por ubicaciones del mapa",
        category = SkillCategory.TOUR,
    ) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val tourId = params["tourId"] as? String
        val tourName = params["tourName"] as? String

        if (tourId.isNullOrBlank() && tourName.isNullOrBlank()) {
            return SkillResult.Error("Se requiere 'tourId' o 'tourName'")
        }

        logInfo("Iniciando tour: id=$tourId name=$tourName")

        return withContext(Dispatchers.Main) {
            val success = when {
                !tourId.isNullOrBlank() -> {
                    logInfo("Reproduciendo tour por ID: $tourId")
                    TemiController.playTourById(tourId)
                }
                !tourName.isNullOrBlank() -> {
                    logInfo("Reproduciendo tour por nombre: $tourName")
                    TemiController.playTourByName(tourName)
                }
                else -> false
            }

            if (success) {
                logInfo("Tour iniciado correctamente")
                SkillResult.Success
            } else {
                val target = tourId ?: tourName
                logWarn("No se pudo iniciar el tour: $target")
                SkillResult.Error("Fallo al iniciar el tour: $target")
            }
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
