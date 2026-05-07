package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para ejecutar secuencias predefinidas del robot Temi.
 *
 * Parámetros:
 *  - sequenceId (String): ID de la secuencia (usa esto si se conoce el ID)
 *  - sequenceName (String): nombre de la secuencia (alternativa al ID)
 *  - withPlayer (Boolean, opcional): mostrar el reproductor de secuencias (default true)
 *  - repeat (Int, opcional): número de repeticiones (default 1)
 *  - startFromStep (Int, opcional): paso inicial (default 1)
 *
 * Requiere permiso: com.robotemi.sdk.permission.SEQUENCE
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "sequence", mapOf("sequenceName" to "Bienvenida"))
 */
class SequenceSkill :
    BaseTemiSkill(
        skillId = "sequence",
        skillName = "Sequence",
        description = "Ejecuta secuencias predefinidas del robot Temi (animaciones, movimientos)",
        category = SkillCategory.SEQUENCE,
    ) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val sequenceId = params["sequenceId"] as? String
        val sequenceName = params["sequenceName"] as? String

        if (sequenceId.isNullOrBlank() && sequenceName.isNullOrBlank()) {
            return SkillResult.Error("Se requiere 'sequenceId' o 'sequenceName'")
        }

        val withPlayer = params["withPlayer"] as? Boolean ?: true
        val repeat = (params["repeat"] as? Number)?.toInt() ?: 1
        val startFromStep = (params["startFromStep"] as? Number)?.toInt() ?: 1

        logInfo("Ejecutando secuencia: id=$sequenceId name=$sequenceName withPlayer=$withPlayer repeat=$repeat")

        return withContext(Dispatchers.Main) {
            val success = when {
                !sequenceId.isNullOrBlank() -> {
                    logInfo("Reproduciendo secuencia por ID: $sequenceId")
                    TemiController.playSequenceById(sequenceId, withPlayer, repeat, startFromStep)
                }
                !sequenceName.isNullOrBlank() -> {
                    logInfo("Reproduciendo secuencia por nombre: $sequenceName")
                    TemiController.playSequenceByName(sequenceName)
                }
                else -> false
            }

            if (success) {
                logInfo("Secuencia iniciada correctamente")
                SkillResult.Success
            } else {
                val target = sequenceId ?: sequenceName
                logWarn("No se pudo ejecutar la secuencia: $target")
                SkillResult.Error("Fallo al ejecutar la secuencia: $target")
            }
        }
    }

    override suspend fun canExecute(context: Context): Boolean {
        val hasPerm = TemiController.isSequencePermissionGranted()
        if (!hasPerm) logWarn("Permiso de secuencias no concedido")
        return hasPerm
    }

    override fun getRequiredPermissions(): List<String> =
        listOf("com.robotemi.sdk.permission.SEQUENCE")
}
