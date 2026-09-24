package com.sandevsystems.omarchyremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sandevsystems.omarchyremote.ui.tr

/**
 * "Find my phone": the PC asked the phone to ring. It rings as an alarm (heard on silent and in Do
 * Not Disturb) at full alarm volume, vibrates, and shows a notification with Stop; after a minute it
 * stops on its own. The alarm volume goes back to what it was.
 */
object Ringer {
    private const val CHANNEL = "encontrar"
    private const val NOTIFICATION = 7_001
    private const val LIMIT_MS = 60_000L
    private val main = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var volumeBefore: Int? = null
    private var powerOff: BroadcastReceiver? = null
    private val _ringing = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Ringing now (RingActivity follows it). */
    val ringing: kotlinx.coroutines.flow.StateFlow<Boolean> = _ringing

    fun start(context: Context) {
        if (ringtone?.isPlaying == true) return
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        volumeBefore = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(app, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            isLooping = true
            play()
        }
        vibrator(app)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        _ringing.value = true
        // The power button silences it, as with an alarm clock (the screen turning off is how it shows).
        powerOff = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = stop(c) }
        runCatching { androidx.core.content.ContextCompat.registerReceiver(app, powerOff,
            android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
        notify(app)
        // Straight to the full screen when Android lets the app open it (the app is in front); else
        // the notification's full-screen intent opens it (over the lock screen, too).
        runCatching { app.startActivity(Intent(app, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ stop(app) }, LIMIT_MS)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        main.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        vibrator(app)?.cancel()
        volumeBefore?.let { app.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        volumeBefore = null
        NotificationManagerCompat.from(app).cancel(NOTIFICATION)
        _ringing.value = false
        powerOff?.let { runCatching { app.unregisterReceiver(it) } }
        powerOff = null
    }

    private fun vibrator(app: Context): android.os.Vibrator? =
        if (android.os.Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(android.os.Vibrator::class.java)

    private fun notify(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Encontrar o celular", "Find my phone"), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) })
        }
        val stop = PendingIntent.getBroadcast(app, 0, Intent(app, StopRinging::class.java), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(app, 1, Intent(app, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_keypad)
            .setContentTitle(tr("O PC está procurando este celular", "Your PC is looking for this phone"))
            .setContentText(tr("Toque em Parar para silenciar.", "Tap Stop to silence it."))
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).setDeleteIntent(stop).setContentIntent(open).setFullScreenIntent(open, true)
            .addAction(0, tr("Parar", "Stop"), stop).build()
        // Without the notification permission it still rings; only the Stop button is missing.
        if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(app).notify(NOTIFICATION, n)
        }
    }

    /** The notification's Stop (and swiping it away). */
    class StopRinging : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = stop(context)
    }
}
