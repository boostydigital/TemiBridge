package com.spatium.deamon.db.temi.core

import android.content.Context
import android.util.Log
import com.spatium.deamon.db.temi.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object RobotPedidosWorker {

    private const val TAG = "RobotPedidosWorker"

    @Volatile
    private var scope: CoroutineScope? = null
    
    // Flag para bloquear polling mientras se procesa un registro
    private val isProcessing = AtomicBoolean(false)

    fun start(context: Context) {
        if (!BuildConfig.ENABLE_SUPABASE_WORKER) {
            Log.d(TAG, "Worker deshabilitado por flag ENABLE_SUPABASE_WORKER")
            return
        }

        if (scope != null) {
            Log.d(TAG, "RobotPedidosWorker ya estaba iniciado")
            return
        }

        val supabase = SupabaseClientProvider.getClient()
        if (supabase == null) {
            Log.w(TAG, "SupabaseClient no disponible; no se inicia worker")
            return
        }

        val appContext = context.applicationContext
        val job = SupervisorJob()
        val newScope = CoroutineScope(Dispatchers.IO + job)
        scope = newScope

        newScope.launch {
            Log.d(TAG, "RobotPedidosWorker iniciado: sync inicial + Realtime")
            runWorker(appContext, supabase)
        }
    }

    fun stop() {
        val currentScope = scope

        Log.d(TAG, "Deteniendo RobotPedidosWorker")

        currentScope?.cancel()
        scope = null
    }

    private suspend fun CoroutineScope.runWorker(context: Context, supabase: SupabaseClient) {
        Log.d(TAG, "Worker iniciado en modo fallback: sync periódica cada 1s (Realtime Postgres no disponible)")

        while (isActive) {
            // Solo procesar si no hay un registro en proceso
            if (!isProcessing.get()) {
                processPendingOnce(context, supabase)
            } else {
                Log.d(TAG, "Saltando polling: hay un registro en proceso")
            }
            try {
                delay(1_000)
            } catch (t: Throwable) {
                Log.w(TAG, "Delay interrumpido en worker: ${t.message}")
            }
        }
    }

    private suspend fun processPendingOnce(context: Context, supabase: SupabaseClient) {
        try {
            val result = supabase
                .from("robot_pedidos")
                .select {
                    filter {
                        eq("realizado", false)
                    }
                }

            val pedidos = result.decodeList<RobotPedido>()
            if (pedidos.isEmpty()) {
                Log.d(TAG, "No hay pedidos pendientes (realizado = false)")
                return
            }

            Log.d(TAG, "Encontrados ${pedidos.size} pedidos pendientes; procesando...")

            for (pedido in pedidos) {
                processPedido(context, supabase, pedido)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error al sincronizar pedidos pendientes: ${t.message}", t)
        }
    }

    private suspend fun processPedido(context: Context, supabase: SupabaseClient, pedido: RobotPedido) {
        // Marcar que estamos procesando para bloquear nuevos polls
        isProcessing.set(true)
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "processPedido INICIANDO: id=${pedido.id}")
            Log.d(TAG, "  secuencia: ${pedido.secuencia}")
            Log.d(TAG, "  comida: ${pedido.comida}")
            Log.d(TAG, "  say: ${pedido.say}")
            Log.d(TAG, "  place: ${pedido.place}")
            Log.d(TAG, "  orden_action: ${pedido.ordenAction}")
            Log.d(TAG, "========================================")

            // Claim atómico: solo un worker marcará realizado=true
            supabase
                .from("robot_pedidos")
                .update(
                    {
                        set("realizado", true)
                    }
                ) {
                    filter {
                        eq("id", pedido.id)
                        eq("realizado", false)
                    }
                }

            Log.d(TAG, "Pedido id=${pedido.id} marcado realizado=true en DB")

            // NUEVO FLUJO:
            // 1. Ejecutar la secuencia del registro PRIMERO
            if (!pedido.secuencia.isNullOrBlank()) {
                Log.d(TAG, "Paso 1: Ejecutando secuencia ${pedido.secuencia}")
                withContext(Dispatchers.Main) {
                    TemiController.playSequenceById(pedido.secuencia)
                }
                // Esperar a que la secuencia cargue e inicie
                delay(5000)
            }
            
            // 2. Abrir MenuActivity con el place del registro
            Log.d(TAG, "Paso 2: Abriendo MenuActivity con place=${pedido.place}")
            withContext(Dispatchers.Main) {
                openMenuActivity(context, pedido.place ?: "")
            }
            
            Log.d(TAG, "Pedido id=${pedido.id} procesado: secuencia ejecutada y MenuActivity abierta")
            
        } catch (t: Throwable) {
            Log.e(TAG, "Error al procesar pedido id=${pedido.id}: ${t.message}", t)
        } finally {
            // Liberar el flag para permitir nuevos polls
            isProcessing.set(false)
            Log.d(TAG, "========================================")
            Log.d(TAG, "Pedido id=${pedido.id} FINALIZADO, polling habilitado")
            Log.d(TAG, "========================================")
        }
    }
    
    private fun openMenuActivity(context: Context, place: String) {
        try {
            val intent = android.content.Intent(context, com.spatium.deamon.db.temi.ui.MenuActivity::class.java).apply {
                putExtra(com.spatium.deamon.db.temi.ui.MenuActivity.EXTRA_PLACE, place)
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
            Log.d(TAG, "MenuActivity lanzada con place=$place")
        } catch (t: Throwable) {
            Log.e(TAG, "Error lanzando MenuActivity: ${t.message}", t)
        }
    }

    // Realtime Postgres (postgresChangeFlow) no se usa en esta versión del cliente.
    // Fallback: se utiliza sincronización periódica en runWorker().
}
