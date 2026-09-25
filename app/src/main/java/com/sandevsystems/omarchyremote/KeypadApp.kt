package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.SeenAgents
import com.sandevsystems.omarchyremote.ui.I18n
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import com.sandevsystems.omarchyremote.ui.KeypadColors
import com.sandevsystems.omarchyremote.ui.ThemePalette
import com.sandevsystems.omarchyremote.ui.tr
import kotlinx.coroutines.delay
import org.json.JSONObject
import com.sandevsystems.omarchyremote.ConnectionState.Connected
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.network.BackgroundLink
import com.sandevsystems.omarchyremote.network.NetworkAddress
import com.sandevsystems.omarchyremote.network.FileTransfer
import com.sandevsystems.omarchyremote.network.NetworkController
import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.Secrets
import com.sandevsystems.omarchyremote.network.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-wide state: the network connection outlives the Activity so the phone can keep
 * following the PC's agents in the background (docs/PLANO-V2.md §4.3).
 */
class KeypadApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs: SharedPreferences by lazy { getSharedPreferences("keypad", Context.MODE_PRIVATE) }
    val network: NetworkController by lazy { NetworkController(scope) }
    val secrets: Secrets by lazy { Secrets(prefs) }
    val files: FileTransfer by lazy { FileTransfer(this, scope) }

    /** Set by the screen that asked for a print to read (OCR); gets the image bytes, or null. */
    var ocrReader: ((ByteArray?) -> Unit)? = null
    val link: BackgroundLink by lazy { BackgroundLink(scope, network, prefs) { secrets.pairingCode } }

    val following get() = prefs.getBoolean("follow_agents", false)
    val pcMedia by lazy { PcMediaPlayer(this) }

    override fun onCreate() {
        super.onCreate()
        // Notifications and the widget can speak before any screen opens.
        I18n.set(I18n.Choice.from(prefs.getString("language", null)))
        SeenAgents.load(prefs.getString("seen_agents", null))
        SeenAgents.onChange = { prefs.edit().putString("seen_agents", it).apply() }
        NetworkAddress.loopbackAllowed = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (following) follow(true)
        // The notification follows the connection and the agents; posted only when its text changes.
        scope.launch { network.state.collect { refreshStatus(); refreshWidget() } }
        scope.launch { network.agents.collect { refreshStatus(); refreshWidget() } }
        scope.launch { network.alerts.collect { agent -> scope.launch { notifier.notify(agent, noticeFor(agent)) } } }
        applySavedTheme()
        scope.launch { network.theme.collect { theme -> theme?.let { (mode, colors) -> applyTheme(mode, colors, save = true) } } }
        scope.launch {
            network.clipboard.collect { clip ->
                val fresh = clipSync.fromPc(clip.text)
                // A copy made on the PC lands here quietly (Android 13+ shows its own "copied").
                if (!clip.auto) copyFromPc(clip.text) else if (fresh && clipSyncOn) setPhoneClip(clip.text)
            }
        }
        scope.launch { network.opens.collect { notifier.open(it) } }
        // The PC's music on the lock screen, while connected and something is loaded there.
        scope.launch {
            kotlinx.coroutines.flow.combine(network.media, network.state) { m, st -> m to (st is Connected) }.collect { (m, connected) ->
                pcMedia.update(m, connected, prefs.getBoolean("pc_media", true))
            }
        }
        // The PC answered or dismissed one of the phone's notifications (NotificationMirror).
        scope.launch { network.phoneReplies.collect { (key, text) -> NotificationMirror.instance?.reply(key, text) } }
        scope.launch { network.phoneDismisses.collect { key -> NotificationMirror.instance?.dismiss(key) } }
        scope.launch { network.rings.collect { if (it) Ringer.start(this@KeypadApp) else Ringer.stop(this@KeypadApp) } }
        scope.launch { network.pcNotifications.collect { if (prefs.getBoolean("pc_notifications", true)) notifier.pc(it) } }
        scope.launch {
            network.fileOffers.collect { offer ->
                val url = prefs.getString("network_address", null)?.let(NetworkAddress::url) ?: return@collect
                val reader = ocrReader
                if (offer.tag == "ocr" && reader != null) files.receive(offer, url, secrets.pairingCode, reader)
                else if (offer.tag != "ocr") files.receive(offer, url, secrets.pairingCode)
            }
        }
        // Battery for Omarchy's bar widget: on connect, then every minute while connected.
        scope.launch {
            while (true) {
                if (network.state.value is Connected) sendBattery()
                delay(60_000)
            }
        }
        scope.launch { network.state.collect { if (it is Connected) sendBattery() } }
    }

    /** Turns background following on or off: the service keeps the process, the link keeps the session. */
    fun follow(on: Boolean) {
        prefs.edit().putBoolean("follow_agents", on).apply()
        if (on) {
            status.reset()
            val text = statusText(network.state.value)
            if (ConnectionService.start(this, text)) status.offer(text)
            link.start()
        } else {
            link.stop()
            ConnectionService.stop(this)
        }
    }

    private val status = StatusText()

    private fun applyTheme(mode: String, colors: Map<String, String>, save: Boolean) {
        val palette = ThemePalette.from(mode, colors) ?: return
        KeypadColors.apply(palette)
        if (save) prefs.edit().putString("theme", JSONObject().put("mode", mode).put("colors", JSONObject(colors)).toString()).apply()
    }

    /** The last theme from the PC, so the app opens in its colors before connecting. */
    private fun applySavedTheme() {
        val saved = prefs.getString("theme", null) ?: return
        runCatching {
            val json = JSONObject(saved)
            val colors = json.getJSONObject("colors")
            applyTheme(json.getString("mode"), colors.keys().asSequence().associateWith { colors.getString(it) }, save = false)
        }
    }

    private val notifier by lazy { AgentNotifier(this) }

    /** What the agent's screen says right now (the command asked, the reply given), for its notification. */
    private suspend fun noticeFor(agent: com.sandevsystems.omarchyremote.network.Agent): com.sandevsystems.omarchyremote.network.AgentNotice {
        network.readAgent(agent.id, 120)
        val text = kotlinx.coroutines.withTimeoutOrNull(2_500) { network.agentText.drop(1).first { it?.first == agent.id } }?.second
        return com.sandevsystems.omarchyremote.network.AgentNotice.of(agent, text?.let { com.sandevsystems.omarchyremote.network.AgentConversation.parse(it) })
    }

    /**
     * Answers an agent from its notification: [key] (allow, deny, or the refusal before a reply),
     * then [reply] as a prompt. Waits a little for the connection (the background link reconnects).
     */
    suspend fun answerAgent(id: String, key: String?, reply: String?, done: String?) {
        val connected = kotlinx.coroutines.withTimeoutOrNull(8_000) { network.state.first { it is Connected } } != null
        if (!connected) return notifier.answered(id, tr("Sem conexão com o PC: abra o app", "No connection to the PC: open the app"))
        if (key != null && !network.agentKeys(id, listOf(key))) return notifier.answered(id, tr("O agente não aceitou", "The agent didn't accept it"))
        if (!reply.isNullOrBlank()) {
            if (key != null) kotlinx.coroutines.delay(700)
            if (!network.agentPrompt(id, reply)) return notifier.answered(id, tr("O agente não aceitou a resposta", "The agent didn't accept the reply"))
            return notifier.answered(id, tr("Resposta enviada", "Reply sent"))
        }
        notifier.answered(id, done ?: tr("Enviado", "Sent"))
    }

    /** The clipboard shared both ways: PC copies land here, phone copies go there (Ajustes). */
    val clipSync by lazy { ClipSync(lastLooked = prefs.getLong("clip_looked", Long.MIN_VALUE), onLooked = { prefs.edit().putLong("clip_looked", it).apply() }) }
    val clipSyncOn get() = prefs.getBoolean("clip_sync", true)

    private fun setPhoneClip(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(tr("Do PC", "From PC"), text))
    }

    enum class ClipSend { SENT, NOTHING, UNREADABLE, TOO_LONG, OFFLINE, FAILED }

    /**
     * The phone's clipboard to the PC's. Android lets only the app in focus read the clipboard
     * (Android 10+): call it with one of our windows focused (any: a sheet is a window too). Not [explicit]: only a copy the PC
     * does not have yet, and never one a password manager marked sensitive.
     */
    suspend fun sendPhoneClip(explicit: Boolean): ClipSend {
        val clipboard = getSystemService(ClipboardManager::class.java)
        // Null too while none of our windows has focus yet: Android hides the clipboard until then.
        val description = clipboard.primaryClipDescription ?: return ClipSend.UNREADABLE
        val stamp = description.timestamp
        if (!explicit && !clipSync.changed(stamp)) return ClipSend.NOTHING
        val sensitive = Build.VERSION.SDK_INT >= 33 && description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        if (!description.hasMimeType("text/*") || (sensitive && !explicit)) {
            clipSync.looked(stamp)
            return ClipSend.NOTHING
        }
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) return ClipSend.NOTHING.also { clipSync.looked(stamp) }
        if (text.length > NetworkSession.CLIPBOARD_SET_LIMIT) return ClipSend.TOO_LONG.also { clipSync.looked(stamp) }
        if (!explicit && !clipSync.toPc(text)) return ClipSend.NOTHING.also { clipSync.looked(stamp) }
        if (network.state.value !is Connected) return ClipSend.OFFLINE
        if (!network.clipboardSet(text)) return ClipSend.FAILED
        clipSync.sent(text)
        clipSync.looked(stamp)
        return ClipSend.SENT
    }

    fun copyFromPc(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, tr("A área de transferência do PC está vazia", "The PC clipboard is empty"), Toast.LENGTH_SHORT).show()
            return
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(tr("Do PC", "From PC"), text))
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, tr("Área de transferência do PC copiada", "PC clipboard copied"), Toast.LENGTH_SHORT).show()
    }

    private fun sendBattery() {
        val battery = getSystemService(BatteryManager::class.java)
        network.phoneStatus(battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), battery.isCharging)
    }

    /** The home screen widget follows the agents, in the theme's colors. */
    private fun refreshWidget() {
        val agents = network.agents.value
        val (title, detail) = AgentsText.widget(network.state.value is Connected, agents)
        // The widget's card is always dark (RemoteViews cannot recolor it everywhere): light text, and the
        // theme's accent only when it is a dark theme's (readable on dark).
        val accent = if (KeypadColors.Light) 0xFFC5F24A.toInt() else KeypadColors.Accent.toArgb()
        AgentsWidget.show(this, title, detail, 0xFFECEEF0.toInt(), 0xFF99A1A8.toInt(), accent,
            urgent = agents.any(AgentsText::needsYou))
    }

    private fun refreshStatus() {
        if (!following) return
        val text = statusText(network.state.value)
        if (status.offer(text)) ConnectionService.update(this, text)
    }

    private fun statusText(state: ConnectionState): String = when (state) {
        is Connected -> AgentsText.status(state.host.name, network.agents.value)
        else -> tr("Reconectando ao computador…", "Reconnecting to the computer…")
    }
}
