package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import android.content.Intent
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.ui.KioskWebActivity
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Skill compuesto para el flujo completo de bienvenida a visitantes.
 *
 * Parámetros:
 *  - text (String, opcional): mensaje de bienvenida (default "¡Bienvenido!")
 *  - place (String, opcional): waypoint al que navegar después del saludo
 *  - infoUrl (String, opcional): URL a mostrar en KioskWebActivity tras el saludo
 *  - infoDelayMs (Long, opcional): ms de espera antes de abrir la URL (default 5_000)
 *  - navDelayMs (Long, opcional): ms de espera entre saludo y navegación (default 3_000)
 *
 * Flujo de ejecución:
 *  1. Habla el texto de bienvenida
 *  2. (Opcional) Espera y abre infoUrl en KioskWebActivity
 *  3. (Opcional) Espera y navega al destino
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "welcome_workflow", mapOf(
 *     "text" to "Bienvenido al evento Spatium, permítame mostrarle el programa",
 *     "infoUrl" to "https://spatium-desk.lovable.app",
 *     "place" to "Sala_Principal"
 *   ))
 */
class WelcomeWorkflowSkill : BaseTemiSkill(
    skillId = "welcome_workflow",
    skillName = "Welcome Workflow",
    description = "Flujo completo de bienvenida: saludo + info web + navegación",
    category = SkillCategory.COMPOSITE
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val text = params["text"] as? String ?: "¡Bienvenido!"
        val place = params["place"] as? String
        val infoUrl = params["infoUrl"] as? String
        val infoDelayMs = (params["infoDelayMs"] as? Number)?.toLong() ?: 5_000L
        val navDelayMs = (params["navDelayMs"] as? Number)?.toLong() ?: 3_000L

        logInfo("WelcomeWorkflow: text='$text' place=$place infoUrl=$infoUrl")

        // Paso 1: Saludo de bienvenida
        logInfo("Paso 1: Hablando saludo de bienvenida")
        withContext(Dispatchers.Main) {
            TemiController.speak(text)
        }

        // Paso 2: Mostrar info web si se especificó
        if (!infoUrl.isNullOrBlank()) {
            if (infoUrl.startsWith("http://") || infoUrl.startsWith("https://")) {
                logInfo("Paso 2: Esperando ${infoDelayMs}ms antes de abrir URL: $infoUrl")
                delay(infoDelayMs)
                withContext(Dispatchers.Main) {
                    try {
                        val intent = Intent(context, KioskWebActivity::class.java).apply {
                            putExtra(KioskWebActivity.EXTRA_URL, infoUrl)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                        context.startActivity(intent)
                        logInfo("KioskWebActivity lanzada con url=$infoUrl")
                    } catch (t: Throwable) {
                        logWarn("No se pudo abrir KioskWebActivity: ${t.message}")
                    }
                }
            } else {
                logWarn("infoUrl inválida (no comienza con http/https): $infoUrl")
            }
        }

        // Paso 3: Navegar al destino si se especificó
        if (!place.isNullOrBlank()) {
            logInfo("Paso 3: Esperando ${navDelayMs}ms antes de navegar a '$place'")
            delay(navDelayMs)
            withContext(Dispatchers.Main) {
                TemiController.goTo(place)
                logInfo("Comando goTo('$place') enviado")
            }
        }

        logInfo("WelcomeWorkflow completado")
        return SkillResult.Success
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
