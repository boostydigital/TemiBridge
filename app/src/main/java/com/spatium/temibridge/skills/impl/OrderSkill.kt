package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import android.content.Intent
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import com.spatium.deamon.db.temi.ui.MenuActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para mostrar la pantalla de menú (MenuActivity).
 *
 * Parámetros:
 *  - place (String, opcional): ubicación del pedido (default "")
 *  - comidaUrl (String, opcional): URL de la imagen del menú
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "order", mapOf("place" to "Sala A"))
 */
class OrderSkill :
    BaseTemiSkill(
        skillId = "order",
        skillName = "Order",
        description = "Abre la pantalla de menú interactiva (MenuActivity)",
        category = SkillCategory.INTERACTION,
    ) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val place = params["place"] as? String ?: ""
        val comidaUrl = params["comidaUrl"] as? String

        logInfo("Abriendo MenuActivity con place='$place' comidaUrl=$comidaUrl")

        return withContext(Dispatchers.Main) {
            try {
                val intent = Intent(context, MenuActivity::class.java).apply {
                    putExtra(MenuActivity.EXTRA_PLACE, place)
                    if (!comidaUrl.isNullOrBlank()) {
                        putExtra("comida_url", comidaUrl)
                    }
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                }
                context.startActivity(intent)
                logInfo("MenuActivity lanzada con place='$place'")
                SkillResult.Success
            } catch (t: Throwable) {
                logError("Error lanzando MenuActivity: ${t.message}", t)
                SkillResult.Error("No se pudo abrir la pantalla de menú: ${t.message}", t)
            }
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
