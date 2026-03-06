package com.spatium.deamon.db.temi

import android.app.Application
import android.util.Log
import com.spatium.deamon.db.temi.core.RobotPedidosForegroundService
import com.spatium.deamon.db.temi.skills.registry.SkillInitializer

class TemiDaemonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            SkillInitializer.initialize()
            Log.d("TemiDaemonApp", "Skills inicializados")
        } catch (t: Throwable) {
            Log.w("TemiDaemonApp", "No se pudo inicializar skills: ${t.message}", t)
        }
        try {
            // Iniciar Foreground Service para mantener el worker activo
            // El service se encarga de iniciar RobotPedidosWorker
            RobotPedidosForegroundService.start(this)
            Log.d("TemiDaemonApp", "Foreground Service iniciado")
        } catch (t: Throwable) {
            Log.w("TemiDaemonApp", "No se pudo iniciar Foreground Service: ${t.message}", t)
        }
    }
}
