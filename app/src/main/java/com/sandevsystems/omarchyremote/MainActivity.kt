package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.ui.I18n
import androidx.core.content.IntentCompat
import android.net.Uri
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.sandevsystems.omarchyremote.ui.KeypadColors
import com.sandevsystems.omarchyremote.ui.KeypadScreen
import com.sandevsystems.omarchyremote.ui.KeypadTheme

class MainActivity : ComponentActivity() {
    private val vm: KeypadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so system bar icons stay light regardless of the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // Also runs again when Android changes the phone's language (the activity is recreated).
        I18n.set(I18n.Choice.from((application as KeypadApp).prefs.getString("language", null)))
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            // Android unregisters the HID app when it leaves the foreground; register again on return.
            override fun onStart(owner: LifecycleOwner) {
                visible = true
                if (Ringer.ringing.value) Ringer.stop(this@MainActivity)  // opened the app: found it
                vm.onForeground()
                vm.offerPhoneClip()
                vm.mirrorOnReturn()
            }

            // Rotation must not drop the connection or release a drag in progress.
            override fun onStop(owner: LifecycleOwner) {
                visible = false
                if (!isChangingConfigurations) vm.onBackground()
            }
        })
        setContent {
            // System bar icons follow the theme: light icons on dark themes, dark on light ones.
            val light = KeypadColors.Light
            LaunchedEffect(light) {
                val bar = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(bar, bar)
            }
            KeypadTheme {
                Box(Modifier.onPreviewKeyEvent { hardwareKey(it.nativeKeyEvent) }) { KeypadScreen(vm) }
            }
        }
        intent?.getStringExtra(EXTRA_AGENT)?.let { vm.pendingAgent = it }
        intent?.getStringExtra(EXTRA_OPEN)?.let { vm.pendingOpen = it }
        shared(intent)
    }

    /**
     * Something shared to Omarchy Remote from another app: text for the PC's clipboard, a link to open
     * there ("Abrir no PC"), or files. Nothing goes before the person confirms (KeypadViewModel.pendingShare),
     * and only content:// from other apps is taken (ShareRules).
     */
    private fun shared(intent: Intent?) {
        fun ok(uri: Uri) = com.sandevsystems.omarchyremote.network.ShareRules.accepts(uri.scheme, uri.authority, packageName)
        val share = when (intent?.action) {
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (stream != null) KeypadViewModel.PendingShare(files = listOf(stream).filter(::ok))
                else if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    KeypadViewModel.PendingShare(text = it, openLink = intent.component?.className?.endsWith(".OpenOnPc") == true)
                }
                else null
            }
            Intent.ACTION_SEND_MULTIPLE ->
                KeypadViewModel.PendingShare(files = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty().filter(::ok))
            else -> return
        }
        if (share != null && (share.text != null || share.files.isNotEmpty())) vm.pendingShare = share
        intent.action = null  // handled: not again after a rotation
    }

    /**
     * A keyboard attached to the phone types on the PC while no text field is open (hardwareKeysToPc).
     * Compose sees the key first when something has focus (root onPreviewKeyEvent), the activity
     * otherwise (onKeyDown/onKeyUp); both end here.
     */
    private fun hardwareKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return event.action != KeyEvent.ACTION_MULTIPLE && vm.volumeKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
        }
        val physical = event.device?.isVirtual == false && event.isFromSource(InputDevice.SOURCE_KEYBOARD)
        val system = event.keyCode in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_APP_SWITCH)
        if (!physical || system || !vm.hardwareKeysToPc) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> vm.hardwareKey(event.keyCode, event.metaState)
            KeyEvent.ACTION_UP -> true
            else -> false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = hardwareKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = hardwareKey(event) || super.onKeyUp(keyCode, event)

    /** Android lets the focused app read the clipboard: a copy made elsewhere goes to the PC now. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.offerPhoneClip()
    }

    /** A tapped agent notification while the app is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_AGENT)?.let { vm.pendingAgent = it }
        intent.getStringExtra(EXTRA_OPEN)?.let { vm.pendingOpen = it }
        shared(intent)
    }

    companion object {
        const val EXTRA_AGENT = "agent"
        const val EXTRA_OPEN = "open"
        /** Whether the activity is on screen (the PC's "open" requests start it only then). */
        @Volatile var visible = false
    }
}
