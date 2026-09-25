package com.sandevsystems.omarchyremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sandevsystems.omarchyremote.ui.tr

/**
 * Foreground service that keeps the process (and the network session) alive while the phone
 * follows the PC's agents. Its notification is the status line: "Ligado a meu-pc · 2 agentes".
 * Type connectedDevice: covered by the BLUETOOTH_CONNECT permission the app already holds.
 */
class ConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: tr("Ligado ao computador", "Connected to the computer")
        val notification = build(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID, notification)
        }
        return START_STICKY
    }

    companion object {
        private const val ID = 1
        const val CHANNEL = "conexao"
        private const val EXTRA_TEXT = "text"

        /**
         * Starts the service. Android 12+ refuses a start from the background (e.g. after the process was
         * restarted without the battery exemption): then the link simply lives while the app is open.
         */
        fun start(context: Context, text: String): Boolean {
            ensureChannel(context)
            val intent = Intent(context, ConnectionService::class.java).putExtra(EXTRA_TEXT, text)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (error: IllegalStateException) {
                Log.w("KeypadService", "foreground service not allowed now: $error")
                false
            }
        }

        /** New text for the running service's notification, without restarting the service. */
        fun update(context: Context, text: String) {
            context.getSystemService(NotificationManager::class.java).notify(ID, build(context, text))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, tr("Conexão com o PC", "PC connection"), NotificationManager.IMPORTANCE_LOW).apply {
                        description = tr("Mostra que o celular está ligado ao computador.", "Shows that the phone is connected to the computer.")
                    },
                )
            }
        }

        private fun build(context: Context, text: String): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_remote)
                .setContentTitle("Omarchy Remote")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }
}
