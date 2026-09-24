package com.sandevsystems.omarchyremote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.content.ContextCompat
import com.sandevsystems.omarchyremote.network.PcMedia
import com.sandevsystems.omarchyremote.ui.tr
import kotlinx.coroutines.launch

/**
 * The PC's music on the phone's lock screen and in the quick settings player: a media session that
 * mirrors what plays on the PC, whose buttons (and headphone buttons) drive the PC's player.
 */
class PcMediaPlayer(private val app: KeypadApp) {
    private var session: MediaSession? = null
    private var shown: PcMedia? = null

    fun update(media: PcMedia?, connected: Boolean, enabled: Boolean) {
        if (media == null || !media.show(connected, enabled)) return hide()
        if (media == shown) return
        shown = media
        val s = session ?: MediaSession(app, "pc").also { created ->
            created.setCallback(object : MediaSession.Callback() {
                override fun onPlay() = command("play-pause")
                override fun onPause() = command("play-pause")
                override fun onSkipToNext() = command("next")
                override fun onSkipToPrevious() = command("previous")
            })
            created.isActive = true
            session = created
        }
        s.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, media.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, media.artist.ifBlank { media.where() })
            .putString(MediaMetadata.METADATA_KEY_ALBUM, media.where())
            .putBitmap(MediaMetadata.METADATA_KEY_ART, cover(media))
            .build())
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(if (media.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build())
        notify(s, media)
    }

    /** A cover: the player's initial on its color (Spotify green, Brave orange…), as the PC tab draws apps. */
    private fun cover(media: PcMedia): android.graphics.Bitmap {
        val name = media.player.lowercase()
        val color = when {
            "spotify" in name -> 0xFF1DB954.toInt()
            "brave" in name -> 0xFFFB542B.toInt()
            "firefox" in name -> 0xFFFF7139.toInt()
            "chrom" in name -> 0xFF4285F4.toInt()
            else -> 0xFF2A2D33.toInt()
        }
        val size = 512
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(color)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = size * 0.46f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val letter = media.player.take(1).uppercase().ifBlank { "♪" }
        c.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bmp
    }

    private fun hide() {
        if (shown == null && session == null) return
        shown = null
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        session?.run { isActive = false; release() }
        session = null
    }

    private fun command(action: String) {
        app.scope.launch { app.network.mediaCmd(action) }
    }

    private fun notify(s: MediaSession, media: PcMedia) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = app.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Mídia do PC", "PC media"), NotificationManager.IMPORTANCE_LOW))
        }
        fun action(icon: Int, label: String, what: String, code: Int) = Notification.Action.Builder(
            Icon.createWithResource(app, icon), label,
            PendingIntent.getBroadcast(app, code, Intent(app, Buttons::class.java).putExtra("action", what), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        val open = app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { PendingIntent.getActivity(app, 20, it, PendingIntent.FLAG_IMMUTABLE) }
        val n = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_keypad)
            .setLargeIcon(cover(media)).setContentTitle(media.title).setContentText(media.artist.ifBlank { media.where() }).setSubText(media.where())
            .setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(media.playing)
            .addAction(action(android.R.drawable.ic_media_previous, tr("Anterior", "Previous"), "previous", 21))
            .addAction(if (media.playing) action(android.R.drawable.ic_media_pause, tr("Pausar", "Pause"), "play-pause", 22)
                else action(android.R.drawable.ic_media_play, tr("Tocar", "Play"), "play-pause", 22))
            .addAction(action(android.R.drawable.ic_media_next, tr("Próxima", "Next"), "next", 23))
            .setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
        manager.notify(NOTIFICATION, n)
    }

    /** The notification's buttons (older Android; newer ones call the session). */
    class Buttons : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext as KeypadApp
            val action = intent.getStringExtra("action") ?: return
            app.scope.launch { app.network.mediaCmd(action) }
        }
    }

    companion object {
        private const val CHANNEL = "midia_pc"
        private const val NOTIFICATION = 7_101
    }
}
