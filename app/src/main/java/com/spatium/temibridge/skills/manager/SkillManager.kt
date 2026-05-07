package com.spatium.deamon.db.temi.skills.manager

import android.content.Context
import android.util.Log
import com.spatium.deamon.db.temi.skills.base.SkillResult
import com.spatium.deamon.db.temi.skills.registry.SkillConfiguration
import com.spatium.deamon.db.temi.skills.registry.SkillRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object SkillManager {

    private const val TAG = "SkillManager"
    private const val DEFAULT_TIMEOUT_MS = 30_000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = ConcurrentLinkedQueue<SkillExecution>()
    private val isProcessing = AtomicBoolean(false)

    data class SkillExecution(
        val skillId: String,
        val params: Map<String, Any>,
        val callback: ((SkillResult) -> Unit)?,
    )

    fun execute(
        context: Context,
        skillId: String,
        params: Map<String, Any> = emptyMap(),
        callback: ((SkillResult) -> Unit)? = null,
    ) {
        Log.d(TAG, "execute() encolando skill=$skillId params=${params.keys}")
        queue.offer(SkillExecution(skillId, params, callback))
        processNext(context)
    }

    fun executeImmediate(
        context: Context,
        skillId: String,
        params: Map<String, Any> = emptyMap(),
        callback: ((SkillResult) -> Unit)? = null,
    ) {
        Log.d(TAG, "executeImmediate() skill=$skillId")
        val skill = SkillRegistry.getSkill(skillId)
        if (skill == null) {
            val err = SkillResult.Error("Skill no registrado: $skillId")
            Log.e(TAG, err.message)
            callback?.invoke(err)
            return
        }

        scope.launch {
            val timeout = SkillConfiguration.getTimeout(skillId).takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
            try {
                val result = withTimeout(timeout) {
                    skill.execute(context, params)
                }
                Log.d(TAG, "executeImmediate() skill=$skillId resultado=${result::class.simpleName}")
                callback?.invoke(result)
            } catch (e: Exception) {
                val err = SkillResult.Error("Timeout o error en skill '$skillId': ${e.message}", e)
                Log.e(TAG, err.message, e)
                callback?.invoke(err)
            }
        }
    }

    private fun processNext(context: Context) {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "processNext() ya hay un skill en proceso")
            return
        }

        val execution = queue.poll()
        if (execution == null) {
            Log.d(TAG, "processNext() cola vacía")
            isProcessing.set(false)
            return
        }

        scope.launch {
            Log.d(TAG, "Procesando skill=${execution.skillId}")
            val skill = SkillRegistry.getSkill(execution.skillId)
            if (skill == null) {
                val err = SkillResult.Error("Skill no registrado: ${execution.skillId}")
                Log.e(TAG, err.message)
                execution.callback?.invoke(err)
                isProcessing.set(false)
                processNext(context)
                return@launch
            }

            val timeout = SkillConfiguration.getTimeout(execution.skillId).takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
            try {
                val result = withTimeout(timeout) {
                    skill.execute(context, execution.params)
                }
                Log.d(TAG, "Skill=${execution.skillId} completado: ${result::class.simpleName}")
                execution.callback?.invoke(result)
            } catch (e: Exception) {
                val err = SkillResult.Error("Error en skill '${execution.skillId}': ${e.message}", e)
                Log.e(TAG, err.message, e)
                execution.callback?.invoke(err)
            } finally {
                isProcessing.set(false)
                processNext(context)
            }
        }
    }

    fun cancelAll() {
        Log.d(TAG, "cancelAll() vaciando cola (${queue.size} pendientes)")
        queue.clear()
    }

    fun isBusy(): Boolean = isProcessing.get() || queue.isNotEmpty()

    fun pendingCount(): Int = queue.size

    fun shutdown() {
        Log.d(TAG, "shutdown() cancelando scope")
        cancelAll()
        scope.cancel()
    }
}
