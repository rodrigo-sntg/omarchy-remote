package com.sandevsystems.omarchyremote

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sandevsystems.omarchyremote.ConnectionState.Connected
import com.sandevsystems.omarchyremote.ui.tr
import kotlinx.coroutines.launch

/**
 * Quick Settings tiles (docs/PLANO-V2.md §8): one tap from the notification shade into the PC's
 * screen or its herdr. They open the app with the same request the Omarchy Phone menu sends.
 */
abstract class OpenTile(private val what: String) : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, what)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class ScreenTile : OpenTile("screen")

class TerminalTile : OpenTile("terminal")

/**
 * "Copiar pro PC": what the phone copied goes to the PC's clipboard. Android lets only the focused
 * app read the clipboard, so a see-through activity takes focus for the moment it needs; without
 * a connection the app itself opens (it sends the copy once it connects).
 */
class ClipTile : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val app = application as KeypadApp
        val target = if (app.network.state.value is Connected) ClipSendActivity::class.java else MainActivity::class.java
        val intent = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class ClipSendActivity : ComponentActivity() {
    private var sending = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || sending) return
        sending = true
        lifecycleScope.launch {
            val text = when ((application as KeypadApp).sendPhoneClip(explicit = true)) {
                KeypadApp.ClipSend.SENT -> tr("Na área de transferência do PC.", "On the PC clipboard.")
                KeypadApp.ClipSend.NOTHING, KeypadApp.ClipSend.UNREADABLE -> tr("Nada para enviar: copie um texto antes.", "Nothing to send: copy some text first.")
                KeypadApp.ClipSend.TOO_LONG -> tr("Texto grande demais para o PC (12 mil caracteres).", "Text too long for the PC (12,000 characters max).")
                KeypadApp.ClipSend.OFFLINE -> tr("Sem conexão com o PC.", "Not connected to the PC.")
                KeypadApp.ClipSend.FAILED -> tr("O PC não recebeu o texto.", "The PC didn't get the text.")
            }
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
