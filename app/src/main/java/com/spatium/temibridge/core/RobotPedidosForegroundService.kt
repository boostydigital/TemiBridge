package com.spatium.deamon.db.temi.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spatium.deamon.db.temi.R
import com.spatium.deamon.db.temi.ui.MainActivity

/**
 * Foreground Service para mantener el worker de pedidos activo.
 * Evita que Android suspenda la app cuando el robot está quieto (Doze mode).
 */
class RobotPedidosForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Foreground Service creado")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Foreground Service iniciado")

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Iniciar el worker si no está corriendo
        RobotPedidosWorker.start(applicationContext)

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Foreground Service destruido")
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Robot Pedidos Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantiene activa la sincronización de pedidos"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Temi Daemon")
            .setContentText("Escuchando pedidos...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TemiDaemon::PedidosWakeLock",
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutos máximo, se renueva automáticamente
            }
            Log.d(TAG, "WakeLock adquirido")
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo adquirir WakeLock: ${t.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock liberado")
                }
            }
            wakeLock = null
        } catch (t: Throwable) {
            Log.w(TAG, "Error liberando WakeLock: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "PedidosFgService"
        private const val CHANNEL_ID = "robot_pedidos_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RobotPedidosForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RobotPedidosForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
