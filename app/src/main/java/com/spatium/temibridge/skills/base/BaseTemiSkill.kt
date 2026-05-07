package com.spatium.deamon.db.temi.skills.base

import android.content.Context
import android.util.Log

abstract class BaseTemiSkill(
    override val skillId: String,
    override val skillName: String,
    override val description: String,
    override val category: SkillCategory = SkillCategory.CUSTOM,
) : TemiSkill {

    private val tag = "Skill[$skillId]"

    protected abstract suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult

    override suspend fun execute(context: Context, params: Map<String, Any>): SkillResult {
        Log.d(tag, "execute() iniciado con params=${params.keys}")
        return try {
            if (!canExecute(context)) {
                val msg = "Skill '$skillName' no puede ejecutarse: prerequisitos no cumplidos"
                Log.w(tag, msg)
                return SkillResult.Error(msg)
            }
            val result = executeSkill(context, params)
            Log.d(tag, "execute() finalizado: ${result::class.simpleName}")
            result
        } catch (e: Exception) {
            Log.e(tag, "execute() excepción no manejada: ${e.message}", e)
            SkillResult.Error("Fallo en ejecución del skill '$skillName': ${e.message}", e)
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true

    override fun getRequiredPermissions(): List<String> = emptyList()

    protected fun logInfo(message: String) = Log.d(tag, message)
    protected fun logWarn(message: String) = Log.w(tag, message)
    protected fun logError(message: String, t: Throwable? = null) = Log.e(tag, message, t)
}
