package com.spatium.deamon.db.temi.core

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RobotPedido(
    val id: Long,
    val secuencia: String?,
    val comida: String?,
    val say: String?,
    @SerialName("orden_action")
    val ordenAction: String?,
    val place: String? = null
)

/**
 * Orquestador de pedidos del robot.
 * Ahora delega la ejecución a CommandQueue para garantizar:
 * - Ejecución secuencial con delays entre comandos
 * - Sistema de cola para múltiples pedidos
 * - Reintentos para apertura de web
 * - SIEMPRE abre la app primero antes de ejecutar comandos
 */
object RobotPedidosOrchestrator {

    private const val TAG = "RobotPedidos"

    /**
     * Ejecuta un pedido de forma SÍNCRONA, esperando a que termine.
     * SIEMPRE abre la app primero, luego ejecuta los comandos en orden.
     * Los comandos se ejecutarán en orden con delays apropiados entre ellos.
     * 
     * @return true si todos los comandos se ejecutaron, false si hubo timeout
     */
    fun executePedidoAndWait(context: Context, pedido: RobotPedido): Boolean {
        Log.d(TAG, "=== INICIANDO PEDIDO id=${pedido.id} ===")
        Log.d(TAG, "  - secuencia: ${pedido.secuencia}")
        Log.d(TAG, "  - comida: ${pedido.comida}")
        Log.d(TAG, "  - say: ${pedido.say}")
        Log.d(TAG, "  - orden_action: ${pedido.ordenAction}")

        val result = CommandQueue.enqueuePedidoAndWait(context, pedido)

        Log.d(TAG, "=== PEDIDO id=${pedido.id} ${if (result) "COMPLETADO" else "TIMEOUT/ERROR"} ===")
        return result
    }
    
    /**
     * Versión legacy que no espera (para compatibilidad con código existente)
     */
    fun executePedido(context: Context, pedido: RobotPedido) {
        Log.d(TAG, "[Legacy] Encolando pedido id=${pedido.id} para ejecución")
        Log.d(TAG, "  - secuencia: ${pedido.secuencia}")
        Log.d(TAG, "  - comida: ${pedido.comida}")
        Log.d(TAG, "  - say: ${pedido.say}")
        Log.d(TAG, "  - orden_action: ${pedido.ordenAction}")

        CommandQueue.enqueuePedido(context, pedido)

        Log.d(TAG, "Pedido id=${pedido.id} encolado. Comandos pendientes: ${CommandQueue.pendingCount()}")
    }
}
