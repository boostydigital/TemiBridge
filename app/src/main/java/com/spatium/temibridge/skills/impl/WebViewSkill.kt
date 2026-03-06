package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import android.content.Intent
import com.spatium.deamon.db.temi.ui.KioskWebActivity
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill para mostrar contenido web en pantalla completa (KioskWebActivity).
 *
 * Parámetros:
 *  - url (String, requerido): URL a mostrar en el WebView
 *  - autoCloseMs (Long, opcional): milisegundos antes de cerrar automáticamente (0 = no cerrar)
 *
 * Ejemplo uso via SkillManager:
 *   SkillManager.execute(context, "webview", mapOf("url" to "https://spatium-desk.lovable.app"))
 */
class WebViewSkill : BaseTemiSkill(
    skillId = "webview",
    skillName = "WebView",
    description = "Muestra contenido web en pantalla completa usando KioskWebActivity",
    category = SkillCategory.INTERACTION
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val url = params["url"] as? String
        if (url.isNullOrBlank()) {
            return SkillResult.Error("Parámetro requerido ausente: 'url'")
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return SkillResult.Error("URL inválida, debe comenzar con http:// o https://: $url")
        }

        logInfo("Abriendo WebView con url=$url")

        return withContext(Dispatchers.Main) {
            try {
                val intent = Intent(context, KioskWebActivity::class.java).apply {
                    putExtra(KioskWebActivity.EXTRA_URL, url)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(intent)
                logInfo("KioskWebActivity lanzada con url=$url")
                SkillResult.Success
            } catch (t: Throwable) {
                logError("Error lanzando KioskWebActivity: ${t.message}", t)
                SkillResult.Error("No se pudo abrir el WebView: ${t.message}", t)
            }
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()
}
