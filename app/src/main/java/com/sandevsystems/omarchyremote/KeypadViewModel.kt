package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.ui.I18n
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.common.InputImage
import android.graphics.BitmapFactory
import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandevsystems.omarchyremote.ConnectionState.Connected
import com.sandevsystems.omarchyremote.bluetooth.BluetoothHidController
import com.sandevsystems.omarchyremote.input.Encoding
import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.KeyboardLayout
import com.sandevsystems.omarchyremote.input.MouseButtons
import com.sandevsystems.omarchyremote.input.PointerAccumulator
import com.sandevsystems.omarchyremote.input.ScrollAccumulator
import com.sandevsystems.omarchyremote.input.RemoteInput
import android.view.Surface
import com.sandevsystems.omarchyremote.display.ControlsLayout
import com.sandevsystems.omarchyremote.display.Loupe
import com.sandevsystems.omarchyremote.input.TypingDiff
import com.sandevsystems.omarchyremote.input.VolumeKeys
import com.sandevsystems.omarchyremote.display.VideoChannel
import com.sandevsystems.omarchyremote.ui.tr
import org.json.JSONObject
import com.sandevsystems.omarchyremote.display.VideoPipe
import com.sandevsystems.omarchyremote.display.VideoQuality
import com.sandevsystems.omarchyremote.display.VideoStats
import com.sandevsystems.omarchyremote.display.displayRequest
import com.sandevsystems.omarchyremote.display.followMonitor
import com.sandevsystems.omarchyremote.network.NetworkAddress
import com.sandevsystems.omarchyremote.network.BackgroundLink
import com.sandevsystems.omarchyremote.network.NetworkController
import com.sandevsystems.omarchyremote.input.HardwareKeys
import com.sandevsystems.omarchyremote.input.TermKeys
import com.sandevsystems.omarchyremote.input.TermModifiers
import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.ChatState
import androidx.compose.ui.graphics.asImageBitmap
import com.sandevsystems.omarchyremote.network.Bind
import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.NowState
import com.sandevsystems.omarchyremote.network.PcWindow
import com.sandevsystems.omarchyremote.network.OmarchyMenu
import com.sandevsystems.omarchyremote.network.PairUri
import com.sandevsystems.omarchyremote.network.TermChannel
import com.sandevsystems.omarchyremote.network.CursorAt
import com.sandevsystems.omarchyremote.network.MonitorInfo
import com.sandevsystems.omarchyremote.network.Workspace
import com.sandevsystems.omarchyremote.input.Shortcuts
import com.sandevsystems.omarchyremote.input.Workspaces
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asImageBitmap
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.sandevsystems.omarchyremote.input.ModifierKeys
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Gesture distances arrive in dp; one dp times [sensitivity] is one HID count. */
enum class Transport { BLUETOOTH, NETWORK }

enum class VideoMode { NONE, DISPLAY, SCREEN }

class KeypadViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("keypad", Context.MODE_PRIVATE)
    private val bluetooth = BluetoothHidController(application)
    private val network = (application as KeypadApp).network

    /** Only one transport is active; switching releases and disconnects the previous one first. */
    var transport by mutableStateOf(
        // A new install starts on the network: it is what gives everything (screen, agents, menu).
        Transport.entries.firstOrNull { it.name == prefs.getString("transport", null) } ?: Transport.NETWORK,
    )
        private set
    private val transportFlow = MutableStateFlow(transport)

    @OptIn(ExperimentalCoroutinesApi::class)
    val connection: StateFlow<ConnectionState> = transportFlow
        .flatMapLatest { if (it == Transport.NETWORK) network.state else bluetooth.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Starting)
    val capsLock: StateFlow<Boolean> = bluetooth.capsLock
    var networkAddress by mutableStateOf(prefs.getString("network_address", "") ?: "")
    private val secrets = getApplication<KeypadApp>().secrets
    var pairingCode by mutableStateOf(secrets.pairingCode)

    var modifiers by mutableIntStateOf(0)
        private set
    var dragging by mutableStateOf(false)
        private set
    var typing by mutableStateOf(false)
        private set
    private var messageState by mutableStateOf<String?>(null)
    /** The banner's text. Set directly, it is a problem (red "!"); [showMessage] is plain information. */
    var message: String?
        get() = messageState
        private set(value) {
            messageState = value
            messageIsError = value != null
        }
    var messageIsError by mutableStateOf(false)
        private set
    var draft by mutableStateOf("")
    var hosts by mutableStateOf(emptyList<Host>())
        private set
    var layout by mutableStateOf(
        KeyboardLayout.entries.firstOrNull { it.name == prefs.getString("layout", null) } ?: KeyboardLayout.ABNT2,
    )
        private set
    var sensitivity by mutableFloatStateOf(prefs.getFloat("sensitivity", 1.2f))
        private set
    val lastHostAddress: String? get() = prefs.getString("last_host", null)

    /** The PC's real workspaces (network only; empty over Bluetooth). */
    val workspaces: StateFlow<List<Workspace>> = network.workspaces

    // Touchpad settings, remembered.
    /** "Acompanhar agentes": keeps the network session in the background (foreground service). */
    var followAgents by mutableStateOf(prefs.getBoolean("follow_agents", false))
        private set

    // ---- Terminal (herdr on the PC), docs/PLANO-V2.md §4.2 ----
    var terminalOpen by mutableStateOf(false)
        private set
    private var term: TermChannel? = null
    private val termPending = mutableListOf<ByteArray>()
    /** Set by the terminal screen once its page is ready; output waits in [termPending] until then. */
    var termSink: ((ByteArray) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                termPending.forEach(value)
                termPending.clear()
            }
        }
    val termModifiers = TermModifiers()

    fun openTerminal() {
        if (videoUrl("term") == null) return  // the message explains: network only
        closeVideo()
        terminalOpen = true
    }

    /** Called by the page once it knows its size: opens the channel to the PC. */
    fun startTerminal(cols: Int, rows: Int) {
        val url = videoUrl("term") ?: return closeTerminal()
        term?.close()
        term = TermChannel(
            url, secrets.pairingCode, cols, rows,
            onOpen = { termActions = it },
            onBytes = { data -> termSink?.invoke(data) ?: termPending.add(data) },
            onClosed = { reason ->
                if (terminalOpen) message = reason
                closeTerminal()
            },
        )
    }

    /** herdr's shortcuts the PC offers for this terminal (TermActions). */
    var termActions by mutableStateOf(emptySet<String>())
        private set

    fun termAction(name: String) {
        term?.action(name)
    }

    fun closeTerminal() {
        terminalOpen = false
        termActions = emptySet()
        term?.close()
        term = null
        termPending.clear()
        termModifiers.ctrl = false
        termModifiers.alt = false
    }

    /** Text typed on the soft keyboard (UTF-8 from the page), with the sticky Ctrl/Alt applied. */
    fun termType(text: String) {
        term?.send(termModifiers.apply(text))
    }

    /** A toolbar key by name (TermKeys); Ctrl/Alt arm the modifiers instead of sending. */
    fun termKey(name: String) {
        when (name) {
            "ctrl" -> termModifiers.ctrl = !termModifiers.ctrl
            "alt" -> termModifiers.alt = !termModifiers.alt
            else -> TermKeys.bytes(name)?.let { term?.send(it) }
        }
    }

    fun termAck(bytes: Int) {
        term?.ack(bytes)
    }

    fun termResize(cols: Int, rows: Int) {
        term?.resize(cols, rows)
    }

    fun changeFollowAgents(value: Boolean) {
        followAgents = value
        getApplication<KeypadApp>().follow(value)
        askBackgroundAccess = value  // turning it on is the one moment to ask for what it needs
    }

    /** Set when "avisar" was just turned on: the screen explains, then asks Android (notifications, battery). */
    var askBackgroundAccess by mutableStateOf(false)

    var naturalScroll by mutableStateOf(prefs.getBoolean("natural_scroll", true))
        private set
    var tapToClick by mutableStateOf(prefs.getBoolean("tap_to_click", true))
        private set
    /** Video screen: touchpad (true) or direct touch; the last one used. */
    var videoTouchpad by mutableStateOf(prefs.getBoolean("video_touchpad", true))
        private set
    /** Português, English, or the phone's language (the default). */
    fun changeLanguage(choice: I18n.Choice) {
        prefs.edit { putString("language", choice.name) }
        I18n.set(choice)
    }

    /** How the person arranged Ver PC's controls (which tools, size, idle behavior, where). */
    var controls by mutableStateOf(ControlsLayout.decode(prefs.getString("ver_pc_controls", null)))
        private set

    fun changeControls(value: ControlsLayout) {
        controls = value
        prefs.edit { putString("ver_pc_controls", value.encode()) }
    }

    /** Ver PC's magnifier around the cursor: 0 = off, else the zoom (display/Loupe.kt). */
    var loupeZoom by mutableFloatStateOf(prefs.getFloat("loupe_zoom", 0f))
        private set

    fun cycleLoupe() {
        loupeZoom = Loupe.next(loupeZoom)
        prefs.edit { putFloat("loupe_zoom", loupeZoom) }
        showMessage(if (loupeZoom == 0f) tr("Lupa desligada.", "Magnifier off.") else tr("Lupa ${loupeZoom.toInt()}× no cursor.", "Magnifier ${loupeZoom.toInt()}× at the cursor."))
    }

    var hintsSeen by mutableStateOf(prefs.getBoolean("hints_seen", false))
        private set
    /** Cursor position on the monitor being viewed (0..1), reported by the PC. */
    var cursor by mutableStateOf<Pair<Float, Float>?>(null)
        private set
    /** "Segurar": left button locked down on the video screen. */
    var videoHold by mutableStateOf(false)
        private set

    /** Full-screen video (network only): the phone as an extra monitor, or viewing one of the PC's monitors. */
    var videoMode by mutableStateOf(VideoMode.NONE)
        private set
    var videoSize by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    /** "Ver PC": the PC's monitors and the one being shown. */
    var monitors by mutableStateOf(emptyList<String>())
        private set
    var currentMonitor by mutableStateOf<String?>(null)
        private set
    /** Ver PC showing only the PC's focused window (its title), following the focus; null: a monitor. */
    var focusedWindow by mutableStateOf<String?>(null)
        private set
    private val video = VideoPipe()
    private var screenLimit = 0 to 0

    /** The PC's monitor layout and where its cursor is (network only), for the cursor map (design §6). */
    val monitorMap: StateFlow<List<MonitorInfo>> = network.monitors
    val cursorAt: StateFlow<CursorAt?> = network.cursor
    val agents: StateFlow<List<Agent>> = network.agents
    val herdrAvailable: StateFlow<Boolean> = network.herdrAvailable
    val agentText: StateFlow<Pair<String, String>?> = network.agentText

    /** The last screen read of each agent (the Agentes tab shows a live excerpt on every card). */
    val agentTexts = androidx.compose.runtime.mutableStateMapOf<String, String>()

    init {
        viewModelScope.launch { network.agentText.collect { it?.let { (id, text) -> agentTexts[id] = text } } }
        viewModelScope.launch {
            network.agentPages.collect { page ->
                val before = chat ?: return@collect
                val after = before.page(page)
                chat = after
                // The first page read: from its end on, new messages come by themselves.
                if (!before.loaded && after.loaded) network.agentFollow(after.id, after.end)
            }
        }
        viewModelScope.launch { network.agentStarted.collect { startedAgent = it } }
        viewModelScope.launch {
            network.thumbs.collect { (monitor, jpeg) ->
                val image: androidx.compose.ui.graphics.ImageBitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
                }
                if (image != null && liveThumbs) thumbs[monitor] = image
            }
        }
        viewModelScope.launch { network.agentCommands.collect { (id, _, items) -> if (items.isNotEmpty()) slashMenus[id] = items } }
        viewModelScope.launch { network.agentItems.collect { (id, items, end) -> chat = chat?.newItems(id, items, end) } }
        viewModelScope.launch { network.agentOutputs.collect { (id, call, text) -> chat?.takeIf { it.id == id }?.let { chat = it.output(call, text) } } }
    }

    /** Asks for an agent's screen for its card (fewer lines than the detail). */
    fun readAgentExcerpt(id: String) = network.readAgent(id, 60)
    /** "screen", "extra" or "terminal" requested by the PC, handled by the screen. */
    var pendingOpen by mutableStateOf<String?>(null)
    /**
     * The open agent's whole conversation, from its session file on the PC: pages as the person
     * scrolls up, new messages pushed while it is open. None (no file): the screen shows instead.
     */
    var chat by mutableStateOf<ChatState?>(null)
        private set

    fun openChat(id: String) {
        if (chat?.id == id && chat?.loaded == true) return
        chat = ChatState(id)
        network.agentHistory(id, null, CHAT_PAGE)
    }

    fun olderChat() {
        val c = chat ?: return
        if (!c.more || c.loading) return
        chat = c.copy(loading = true)
        network.agentHistory(c.id, c.start, CHAT_PAGE)
    }

    fun closeChat() {
        if (chat != null) network.agentUnfollow()
        chat = null
    }

    fun chatOutput(call: String, at: Long) {
        chat?.let { network.agentOutput(it.id, at, call) }
    }

    /** An agent the app should open on arrival (tapped notification). */
    var pendingAgent by mutableStateOf<String?>(null)

    /** Enough lines that the agent's message survives the TUI chrome below it (AgentScreen). */
    fun readAgent(id: String) = network.readAgent(id, 120)

    val omarchyMenu: StateFlow<OmarchyMenu?> = network.menu
    val binds: StateFlow<List<Bind>> = network.binds
    val pcWindows: StateFlow<List<PcWindow>> = network.windows

    fun loadBinds() = network.bindsGet()

    val now: StateFlow<NowState?> = network.now

    fun loadNow() = network.nowGet()

    /** Whether the phone's volume buttons move the PC's volume while connected. */
    var volumeKeysToPc by mutableStateOf(prefs.getBoolean("volume_keys_to_pc", true))
        private set

    fun changeVolumeKeysToPc(value: Boolean) {
        volumeKeysToPc = value
        prefs.edit { putBoolean("volume_keys_to_pc", value) }
    }

    /** The PC's notifications also show on the phone ("Avisos do PC"). */
    var pcNotifications by mutableStateOf(prefs.getBoolean("pc_notifications", true))
        private set

    fun changePcNotifications(value: Boolean) {
        pcNotifications = value
        prefs.edit { putBoolean("pc_notifications", value) }
    }

    /** The PC's screen and the terminal kept out of screenshots, screen recordings and the recents preview. */
    var secureScreens by mutableStateOf(prefs.getBoolean("secure_screens", true))
        private set

    fun changeSecureScreens(value: Boolean) {
        secureScreens = value
        prefs.edit { putBoolean("secure_screens", value) }
    }

    /** Something another app shared, waiting for the person to confirm before it goes to the PC. */
    data class PendingShare(val text: String? = null, val openLink: Boolean = false, val files: List<android.net.Uri> = emptyList())

    var pendingShare by mutableStateOf<PendingShare?>(null)

    fun confirmShare() {
        val share = pendingShare ?: return
        pendingShare = null
        when {
            share.files.isNotEmpty() -> sendFiles(share.files)
            share.text != null && share.openLink -> openUrlOnPc(share.text)
            share.text != null -> sendClipboardToPc(share.text)
        }
    }

    /** The PC's lock screen and unlocking it with the fingerprint (network UnlockKey, host unlock.py). */
    val pcLock: StateFlow<com.sandevsystems.omarchyremote.network.PcLock?> = network.pcLock

    enum class Unlock { IDLE, ASKING_PC, FINGER, WAITING, ENROLLING }
    var unlockStep by mutableStateOf(Unlock.IDLE)
        private set
    private var unlockActivity: java.lang.ref.WeakReference<android.app.Activity>? = null

    init {
        viewModelScope.launch {
            network.unlockReplies.collect { r ->
                when (r.type) {
                    "unlock.enrolled" -> {
                        unlockStep = Unlock.IDLE
                        if (r.ok) showMessage(tr("Pronto: este celular pode desbloquear o PC.", "Done: this phone can unlock the PC."))
                        else message = when (r.reason) {
                            "denied" -> tr("Recusado no PC.", "Refused on the PC.")
                            "locked" -> tr("Desbloqueie o PC para confirmar lá.", "Unlock the PC to confirm there.")
                            else -> tr("O PC não aceitou a chave.", "The PC didn't take the key.")
                        }
                    }
                    "unlock.challenge" -> {
                        // Only an answer to our own request: a PC can't pop the fingerprint prompt by itself.
                        if (unlockStep != Unlock.ASKING_PC) return@collect
                        val activity = unlockActivity?.get()
                        unlockActivity = null
                        if (!r.ok || r.nonce == null || activity == null) {
                            unlockStep = Unlock.IDLE
                            if (r.reason == "not-enrolled") { com.sandevsystems.omarchyremote.network.UnlockKey.delete() }
                            message = unlockWhy(r.reason)
                            return@collect
                        }
                        unlockStep = Unlock.FINGER
                        com.sandevsystems.omarchyremote.network.UnlockKey.sign(activity, pcName(),
                            com.sandevsystems.omarchyremote.network.UnlockKey.hostName(networkAddress), r.nonce) { signature, why ->
                            if (signature == null) {
                                unlockStep = Unlock.IDLE
                                if (why == "invalidated") message = tr("Uma digital nova foi cadastrada no celular: configure o desbloqueio de novo.",
                                    "A new fingerprint was added on the phone: set up unlocking again.")
                                else if (why != "cancelled") message = why
                            } else {
                                unlockStep = Unlock.WAITING
                                network.unlockRespond(signature)
                            }
                        }
                    }
                    "unlock.result" -> {
                        unlockStep = Unlock.IDLE
                        if (r.ok) showMessage(tr("PC desbloqueado.", "PC unlocked.")) else message = unlockWhy(r.reason)
                    }
                }
            }
        }
    }

    private fun unlockWhy(reason: String?) = when (reason) {
        "not-locked" -> tr("O PC já está desbloqueado.", "The PC is already unlocked.")
        "not-enrolled" -> tr("Este celular não está cadastrado para desbloquear o PC.", "This phone isn't set up to unlock the PC.")
        "too-many" -> tr("Muitas tentativas: espere um minuto.", "Too many tries: wait a minute.")
        "bad-signature" -> tr("O PC não reconheceu a chave deste celular.", "The PC didn't recognize this phone's key.")
        "not-accepted" -> tr("O bloqueio do PC não aceitou. Tente de novo.", "The PC's lock didn't take it. Try again.")
        else -> tr("Não deu para desbloquear o PC.", "Couldn't unlock the PC.")
    }

    private fun pcName() = networkAddress.substringBefore('.').ifBlank { "PC" }

    /** Sends this phone's public key; someone at the (unlocked) PC confirms it there. */
    fun enrollUnlock() {
        unlockStep = Unlock.ENROLLING
        val key = runCatching { com.sandevsystems.omarchyremote.network.UnlockKey.publicKey() }.getOrNull()
        if (key == null) {
            unlockStep = Unlock.IDLE
            message = tr("Cadastre uma digital no celular primeiro.", "Add a fingerprint on the phone first.")
            return
        }
        network.unlockEnroll(key)
    }

    /** Unlocks the PC: a challenge from it, the fingerprint here, the signature back. */
    fun unlockPc(activity: android.app.Activity) {
        if (unlockStep != Unlock.IDLE) return
        unlockActivity = java.lang.ref.WeakReference(activity)
        unlockStep = Unlock.ASKING_PC
        network.unlockChallenge()
    }

    /** The phone's notifications on the PC (NotificationMirror); needs Android's notification access. */
    var phoneNotifications by mutableStateOf(prefs.getBoolean(NotificationMirror.PREF, false))
        private set

    fun mirrorAccess(): Boolean = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(getApplication())
        .contains(getApplication<KeypadApp>().packageName)

    /** Asked for Android's access: turned on by itself when the person comes back with it given
     *  (kept in prefs: giving the access may restart the app's process). */
    private var mirrorPending: Boolean
        get() = prefs.getBoolean("mirror_pending", false)
        set(value) = prefs.edit { putBoolean("mirror_pending", value) }

    /** False when Android's access is missing: the screen explains it and opens the system setting. */
    fun changePhoneNotifications(value: Boolean): Boolean {
        if (value && !mirrorAccess()) { mirrorPending = true; return false }
        phoneNotifications = value
        prefs.edit { putBoolean(NotificationMirror.PREF, value) }
        return true
    }

    fun mirrorOnReturn() {
        if (mirrorPending && mirrorAccess()) {
            mirrorPending = false
            changePhoneNotifications(true)
            showMessage(tr("Notificações do celular vão aparecer no PC.", "Phone notifications will show on the PC."))
        }
    }

    /** The apps that notified on this phone (package, name), and whether each goes to the PC. */
    fun mirrorApps(): List<Pair<String, String>> {
        val pm = getApplication<KeypadApp>().packageManager
        return prefs.getStringSet(NotificationMirror.SEEN, emptySet())!!.map { pkg ->
            pkg to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }.sortedBy { it.second.lowercase() }
    }

    var mirrorExcluded by mutableStateOf(prefs.getStringSet(NotificationMirror.EXCLUDED, emptySet())!!.toSet())
        private set

    fun setMirrorApp(pkg: String, on: Boolean) {
        mirrorExcluded = if (on) mirrorExcluded - pkg else mirrorExcluded + pkg
        prefs.edit { putStringSet(NotificationMirror.EXCLUDED, mirrorExcluded) }
    }

    /** The PC's music on the phone's lock screen (KeypadApp.pcMedia). */
    var pcMedia by mutableStateOf(prefs.getBoolean("pc_media", true))
        private set

    fun changePcMedia(value: Boolean) {
        pcMedia = value
        prefs.edit { putBoolean("pc_media", value) }
        getApplication<KeypadApp>().pcMedia.update(network.media.value, connection.value is Connected, value)
    }

    /** Copies cross between the phone and the PC by themselves (KeypadApp.clipSync). */
    var clipSync by mutableStateOf(prefs.getBoolean("clip_sync", true))
        private set

    fun changeClipSync(value: Boolean) {
        clipSync = value
        prefs.edit { putBoolean("clip_sync", value) }
        if (value) offerPhoneClip()
    }

    private var offering: Job? = null

    /** A new copy on the phone goes to the PC: when the app comes back, gets focus, or connects on screen. */
    fun offerPhoneClip() {
        val app = getApplication<KeypadApp>()
        if (!app.clipSyncOn || transport != Transport.NETWORK || connection.value !is Connected || !MainActivity.visible) return
        offering?.cancel()
        offering = viewModelScope.launch {
            // Back on screen, focus (and with it the clipboard) comes a moment later.
            repeat(4) {
                delay(250)
                when (app.sendPhoneClip(explicit = false)) {
                    KeypadApp.ClipSend.UNREADABLE -> {}
                    KeypadApp.ClipSend.SENT -> return@launch showMessage(tr("O que você copiou já está no PC.", "What you copied is on the PC now."))
                    else -> return@launch
                }
            }
        }
    }

    /** Presentation mode: the volume buttons change slides (down = next). Not kept across launches. */
    var presenting by mutableStateOf(false)
        private set

    fun togglePresenting() {
        presenting = !presenting
        showMessage(if (presenting) tr("Modo apresentação: volume − avança, volume + volta.", "Presentation mode: volume − goes forward, volume + goes back.") else tr("Modo apresentação desligado.", "Presentation mode off."))
    }

    /** A volume button; true when it went to the PC (the phone's own volume stays put). */
    fun volumeKey(keyCode: Int, down: Boolean): Boolean {
        val target = VolumeKeys.target(keyCode, input != null && connection.value is Connected, volumeKeysToPc, presenting) ?: return false
        if (!down) return true
        when (target) {
            VolumeKeys.Target.VolumeUp -> media("volume-up")
            VolumeKeys.Target.VolumeDown -> media("volume-down")
            VolumeKeys.Target.NextSlide -> pressShortcut(KeyStroke(USAGE_RIGHT, 0))
            VolumeKeys.Target.PreviousSlide -> pressShortcut(KeyStroke(USAGE_LEFT, 0))
        }
        return true
    }

    /** A print of the PC at full resolution, saved in Pictures (notification to open/share it).
     * [monitor] null: all monitors. */
    fun printPc(monitor: String? = null) {
        if (videoUrl("") == null) return
        viewModelScope.launch {
            if (network.shotGet(monitor, null, "save")) showMessage(tr("Print a caminho (Imagens/Omarchy Remote).", "Screenshot on its way (Pictures/Omarchy Remote)."))
            else message = tr("O PC não tirou o print.", "The PC didn't take the screenshot.")
        }
    }

    /** "Copiar texto da tela": the text read, shown in a sheet (null: no sheet). */
    var screenText by mutableStateOf<String?>(null)
    var readingText by mutableStateOf(false)
        private set

    /** Reads the text in [region] of the monitor on Ver PC: a full-resolution print of just that
     * area, recognized on the phone (ML Kit, nothing leaves it). */
    fun readScreenText(region: FloatArray) {
        val monitor = currentMonitor ?: return
        if (videoUrl("") == null || readingText) return
        val app = getApplication<KeypadApp>()
        readingText = true
        app.ocrReader = { bytes ->
            app.ocrReader = null
            recognize(bytes)
        }
        viewModelScope.launch {
            if (!network.shotGet(monitor, region, "ocr")) {
                app.ocrReader = null
                readingText = false
                message = tr("O PC não tirou o print para ler.", "The PC didn't take the screenshot to read.")
            }
        }
    }

    private fun recognize(bytes: ByteArray?) {
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap == null) {
            readingText = false
            message = tr("Não recebi a imagem do PC.", "Didn't get the image from the PC.")
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                readingText = false
                if (result.text.isBlank()) showMessage(tr("Nenhum texto encontrado nessa área.", "No text found in that area.")) else screenText = result.text
            }
            .addOnFailureListener {
                readingText = false
                message = tr("Não consegui ler o texto.", "Couldn't read the text.")
            }
    }

    fun copyToPhone(text: String) = getApplication<KeypadApp>().copyFromPc(text)

    /** The AI plans' limits (ai-usagebar on the PC), shown in Agentes and Omarchy → Agora. */
    val usage = network.usage

    fun refreshUsage(force: Boolean = false) = network.usageGet(force)

    /** Lock or suspend the PC ("Agora" → PC). */
    fun pcAct(action: String) {
        viewModelScope.launch {
            if (!network.pcAct(action)) message = tr("O PC não respondeu.", "The PC didn't respond.")
            else if (action == "lock") showMessage(tr("PC bloqueado.", "PC locked."))
        }
    }

    fun media(action: String) {
        viewModelScope.launch { network.mediaCmd(action) }
    }

    fun loadWindows() = network.windowsGet()

    /** An Omarchy keybinding, pressed on the PC as its keys. */
    fun runBind(bind: Bind) {
        pressShortcut(bind.stroke)
        showMessage("${bind.keys} · ${bind.description}")
    }

    fun windowAct(window: PcWindow, action: String, workspace: Int? = null) {
        viewModelScope.launch { if (!network.windowAct(window.address, action, workspace)) showMessage(tr("O PC não fez isso na janela.", "The PC didn't do that to the window.")) }
    }

    /** Asks the PC for Omarchy's menu (fresh each time: when/checked change). */
    fun loadOmarchyMenu() = network.menuGet()

    fun runMenuItem(id: String, label: String) {
        viewModelScope.launch { showMessage(if (network.menuRun(id)) tr("$label · no PC", "$label · on the PC") else tr("O PC não abriu \"$label\".", "The PC didn't open \"$label\".")) }
    }

    /** The real Omarchy menu on the PC at [route]; the screen then opens Ver PC to show it. */
    fun showMenuOnPc(route: String) {
        viewModelScope.launch { if (!network.menuShow(route.ifEmpty { "root" })) showMessage(tr("O PC não abriu o menu.", "The PC didn't open the menu.")) }
    }

    /** Text to the PC's clipboard (shared from another app, or the phone's own clipboard). */
    fun sendClipboardToPc(text: String) {
        if (text.isBlank()) return showMessage(tr("Nada para enviar: a área de transferência está vazia.", "Nothing to send: the clipboard is empty."))
        if (text.length > NetworkSession.CLIPBOARD_SET_LIMIT) return showMessage(tr("Texto grande demais para a área de transferência do PC (12 mil caracteres).", "Text too long for the PC clipboard (12,000 characters max)."))
        if (transport != Transport.NETWORK || connection.value !is Connected) return showMessage(tr("Conecte-se pela rede para usar a área de transferência do PC.", "Connect over the network to use the PC clipboard."))
        viewModelScope.launch {
            showMessage(if (network.clipboardSet(text)) tr("Na área de transferência do PC.", "On the PC clipboard.") else tr("O PC não recebeu o texto.", "The PC didn't get the text."))
        }
    }

    /** "Copiar": Omarchy's universal copy (Super+C, terminals too) on the PC, then that copy here. */
    fun copyOnPc() {
        typeShortcut("c", ModifierKeys.SUPER)
        if (transport != Transport.NETWORK || connection.value !is Connected) return
        viewModelScope.launch {
            delay(450)  // the app on the PC takes a moment to put it on the clipboard
            network.clipboardGet()
        }
    }

    /** Omarchy's universal cut (Super+X), then the cut text here, like Copiar. */
    fun cutOnPc() {
        typeShortcut("x", ModifierKeys.SUPER)
        if (transport != Transport.NETWORK || connection.value !is Connected) return
        viewModelScope.launch {
            delay(450)
            network.clipboardGet()
        }
    }

    /** "Colar": the phone's copy to the PC's clipboard, then Omarchy's universal paste (Super+V). */
    fun pasteOnPc() {
        if (transport != Transport.NETWORK) return typeShortcut("v", ModifierKeys.SUPER)
        viewModelScope.launch {
            val outcome = PcClipboard.afterSend(getApplication<KeypadApp>().sendPhoneClip(explicit = true))
            if (outcome.paste) typeShortcut("v", ModifierKeys.SUPER)
            outcome.message?.let { showMessage(it) }
        }
    }

    /** The PC's clipboard to the phone's (it arrives and is copied by KeypadApp). */
    fun copyPcClipboard() {
        if (transport != Transport.NETWORK || connection.value !is Connected) return showMessage(tr("Conecte-se pela rede para usar a área de transferência do PC.", "Connect over the network to use the PC clipboard."))
        network.clipboardGet()
    }

    /** The cursor map is on screen (the PC stops reading the cursor otherwise). */
    fun watchCursor(on: Boolean) = network.watchCursor(on)

    fun agentKeys(id: String, keys: List<String>) {
        viewModelScope.launch {
            // herdr takes up to 8 keys at a time (a long walk down a picker is more).
            for (chunk in keys.chunked(8)) {
                if (!network.agentKeys(id, chunk)) return@launch showMessage(tr("O herdr não aceitou a tecla.", "herdr didn't accept the key."))
            }
        }
    }

    fun agentPrompt(id: String, text: String) {
        viewModelScope.launch {
            showMessage(if (network.agentPrompt(id, text)) tr("Prompt enviado.", "Prompt sent.") else tr("O agente não aceitou o prompt agora.", "The agent didn't accept the prompt right now."))
        }
    }

    fun focusAgent(id: String) {
        viewModelScope.launch { network.focusAgent(id) }
    }

    /** The editable row of five one-tap shortcuts (design §3 ShortcutRow, §5 Editar atalhos). */
    var shortcutSlots by mutableStateOf(Shortcuts.parseSlots(prefs.getString("shortcut_slots", null)).map(Shortcuts::byId))
        private set

    private val pointer = PointerAccumulator()
    private val wheel = ScrollAccumulator(stepDistance = SCROLL_STEP_DP)
    private var typingJob: Job? = null
    private var clickJob: Job? = null
    private var foreground = false
    private var autoReconnectTried = false
    private var coldStart = true

    /** Persisted: Samsung may kill the process while the app is in the background. */
    private var reconnectOnReturn: Boolean
        get() = prefs.getBoolean("reconnect_on_return", false)
        set(value) = prefs.edit { putBoolean("reconnect_on_return", value) }

    init {
        network.onSoftError = { showMessage(it) }
        viewModelScope.launch {
            connection.collect { state ->
                if (state is Connected) {
                    if (transport == Transport.BLUETOOTH) prefs.edit { putString("last_host", state.host.address) }
                    reconnectOnReturn = false
                    wasConnected = true
                    offerPhoneClip()
                    if (transport == Transport.NETWORK) network.hostGet()
                    if (waking != Waking.IDLE) cancelWake()
                    // A new session knows nothing of the open agent: read it again and follow it.
                    chat?.let { chat = null; openChat(it.id) }
                } else {
                    resetControls()
                    // The PC dropped a live session (its host restarted, the network blinked): try again.
                    if (state is ConnectionState.Disconnected && wasConnected && transport == Transport.NETWORK) retryDropped()
                    if (state is ConnectionState.Disconnected) wasConnected = false
                    // Android may drop the registration just after we returned to the foreground.
                    if (state is ConnectionState.Disconnected && foreground) reconnectIfWanted()
                }
            }
        }
    }

    private val input: RemoteInput?
        get() = if (transport == Transport.NETWORK) network.input else bluetooth.input

    fun start() {
        if (transport == Transport.BLUETOOTH) bluetooth.start()
    }

    fun chooseTransport(value: Transport) {
        if (value == transport) return
        releaseAll()
        reconnectOnReturn = false
        // Never two transports at once: the phone also stops being a Bluetooth HID device.
        if (transport == Transport.BLUETOOTH) bluetooth.stop() else network.disconnect()
        transport = value
        transportFlow.value = value
        prefs.edit { putString("transport", value.name) }
        start()
    }

    fun onForeground() {
        foreground = true
        autoReconnectTried = false
        start()
        if (connection.value !is Connected) reconnectIfWanted()
    }

    fun onBackground() {
        foreground = false
        closeVideo()
        closeTerminal()
        if (connection.value is Connected) reconnectOnReturn = true
        releaseAll()
        // Bluetooth HID is dropped by Android in the background; the network session is closed the same way.
        // Following agents keeps the session; otherwise the network is closed like Bluetooth is.
        if (transport == Transport.NETWORK && !followAgents) network.disconnect()
    }

    /** At most one automatic attempt per return to the app, so a failing host does not loop. */
    private fun reconnectIfWanted() {
        // A fresh process over the network with saved credentials is also a "return": nothing to pair, one quiet try.
        val fresh = coldStart && transport == Transport.NETWORK && !prefs.getString("network_address", null).isNullOrBlank()
        coldStart = false
        if (!(reconnectOnReturn || fresh) || autoReconnectTried) return
        autoReconnectTried = true
        reconnectLast()
    }

    private var wasConnected = false
    private var retryJob: Job? = null

    /** While the app is open and nobody pressed "Desconectar": tries every few seconds until it connects. */
    private fun retryDropped() {
        if (retryJob?.isActive == true) return
        val link = getApplication<KeypadApp>().link
        retryJob = viewModelScope.launch {
            var attempt = 0
            while (foreground && !link.held && connection.value !is Connected) {
                delay(BackgroundLink.retryDelayMs(attempt++))
                if (!foreground || link.held || connection.value is Connected) break
                if (transport == Transport.NETWORK && connection.value is ConnectionState.Disconnected) reconnectLast()
            }
        }
    }

    private fun reconnectLast() {
        if (transport == Transport.NETWORK) {
            // Saved values, not whatever is half-typed in the fields.
            val address = prefs.getString("network_address", null)
            if (!address.isNullOrBlank()) network.connect(address, secrets.pairingCode)
        } else {
            lastHostAddress?.let { bluetooth.connect(it) }
        }
    }

    /** How to wake the PC (from its last host.get), kept for when it is asleep or off. */
    var wakeTargets by mutableStateOf(com.sandevsystems.omarchyremote.network.Wake.targets(
        prefs.getString("wake_targets", null)?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }))
        private set

    enum class Waking { IDLE, WAKING, FAILED }
    var waking by mutableStateOf(Waking.IDLE)
        private set
    private var wakeJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            network.host.collect { h ->
                if (h != null && h.wake.isNotEmpty() && h.wake != wakeTargets) {
                    wakeTargets = h.wake
                    prefs.edit { putString("wake_targets", com.sandevsystems.omarchyremote.network.Wake.save(h.wake)) }
                }
            }
        }
    }

    /**
     * Sends the magic packet a few times (a sleeping card may miss the first), then keeps reconnecting
     * for up to 75 s; the PC's screen takes over when it answers.
     */
    fun wakePc() {
        if (wakeTargets.isEmpty()) return
        wakeJob?.cancel()
        waking = Waking.WAKING
        message = null
        getApplication<KeypadApp>().link.held = false
        wakeJob = viewModelScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            var n = 0
            while (android.os.SystemClock.elapsedRealtime() - started < 75_000) {
                if (connection.value is Connected) { waking = Waking.IDLE; return@launch }
                if (n < 3) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.sandevsystems.omarchyremote.network.Wake.send(wakeTargets) }
                }
                if (n % 3 == 2 && connection.value !is ConnectionState.Connecting) { start(); reconnectLast() }
                n++
                kotlinx.coroutines.delay(1_500)
            }
            waking = if (connection.value is Connected) Waking.IDLE else Waking.FAILED
        }
    }

    fun cancelWake() {
        wakeJob?.cancel()
        waking = Waking.IDLE
    }

    /** Explicit retry: registers again if needed and reconnects to the last host, if any. */
    fun retry() {
        message = null
        getApplication<KeypadApp>().link.held = false
        start()
        reconnectLast()
    }

    /** The pairing QR from Omarchy (Phone → Pair phone): fills address and code, then connects. */
    fun pairFromQr(text: String): Boolean {
        val (host, code) = PairUri.parse(text) ?: return false
        if (transport != Transport.NETWORK) chooseTransport(Transport.NETWORK)
        networkAddress = host
        pairingCode = code
        connectNetwork()
        return true
    }

    /** Connecting is what switches the transport (choosing a tab only looks). */
    fun connectNetwork() {
        if (transport != Transport.NETWORK) chooseTransport(Transport.NETWORK)
        message = null
        getApplication<KeypadApp>().link.held = false
        prefs.edit { putString("network_address", networkAddress.trim()) }
        secrets.pairingCode = pairingCode.trim().uppercase()
        network.connect(networkAddress, pairingCode.trim().uppercase())
    }

    fun loadHosts() {
        hosts = bluetooth.bondedHosts()
    }

    fun connect(host: Host) {
        if (transport != Transport.BLUETOOTH) chooseTransport(Transport.BLUETOOTH)
        message = null
        bluetooth.connect(host.address)
    }

    fun disconnect() {
        reconnectOnReturn = false
        getApplication<KeypadApp>().link.held = true  // "Desconectar" means it, even while following agents
        releaseAll()
        if (transport == Transport.NETWORK) network.disconnect() else bluetooth.disconnect()
    }

    /** Files shared from any app: to the PC's Downloads (progress in a notification). */
    fun sendFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val url = videoUrl("") ?: return
        getApplication<KeypadApp>().files.send(uris, url.trimEnd('/'), secrets.pairingCode)
    }

    /** Each agent's "/" menu, as the PC listed it (agent id → entries). */
    val slashMenus = androidx.compose.runtime.mutableStateMapOf<String, List<com.sandevsystems.omarchyremote.network.SlashCommand>>()

    fun loadSlashMenu(id: String) = network.agentCommands(id)

    /** The open agent's project in git, and the diff of the file last asked. */
    val agentGit: StateFlow<Pair<String, com.sandevsystems.omarchyremote.network.GitSummary>?> = network.agentGit
    val agentGitDiff: StateFlow<Triple<String, String, String>?> = network.agentGitDiff

    fun loadGit(id: String) = network.agentGit(id)

    fun loadGitDiff(id: String, path: String) = network.agentGitDiff(id, path)

    /** The monitors' live thumbnails (monitor name → image), on Wi-Fi only. */
    val thumbs = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()

    /** Ajustes: thumbnails of the PC's monitors in the maps, on Wi-Fi (never on mobile data). */
    var liveThumbs by mutableStateOf(prefs.getBoolean("live_thumbs", true))
        private set

    fun changeLiveThumbs(value: Boolean) {
        liveThumbs = value
        prefs.edit { putBoolean("live_thumbs", value) }
        if (!value) thumbs.clear()
    }

    /** Wi-Fi (or any network Android does not meter): where thumbnails are worth their bytes. */
    fun unmetered(): Boolean =
        !getApplication<KeypadApp>().getSystemService(android.net.ConnectivityManager::class.java).isActiveNetworkMetered

    fun requestThumb(monitor: String, width: Int) {
        if (transport == Transport.NETWORK && connection.value is Connected) network.thumbGet(monitor, width)
    }

    /** How the PC is doing (CPU, memory, temperature, battery), read while the PC tab shows. */
    val pcStats: StateFlow<com.sandevsystems.omarchyremote.network.PcStats?> = network.stats

    fun loadStats() = network.statsGet()

    /** The PC service's version; outdated: new code waits for a restart. */
    val hostInfo: StateFlow<com.sandevsystems.omarchyremote.network.HostInfo?> = network.host

    fun restartHost() {
        viewModelScope.launch {
            if (network.hostRestart()) showMessage(tr("Reiniciando o serviço do PC… volta em alguns segundos.", "Restarting the PC service… back in a few seconds."))
            else showMessage(tr("O serviço do PC não reiniciou.", "The PC service didn't restart."))
        }
    }

    /** The PC's folders, browsed to bring a file over. */
    val pcFiles: StateFlow<com.sandevsystems.omarchyremote.network.FolderListing?> = network.files

    fun listPcFiles(path: String?) = network.filesList(path)

    /** Asks the PC for a file; the transfer then shows in [transfers]. [onAsked] says whether the PC took the request. */
    fun fetchFromPc(path: String, onAsked: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = network.filesFetch(path)
            onAsked(ok)
            if (!ok) message = tr("O PC não entregou esse arquivo.", "The PC didn't hand that file over.")
        }
    }

    /** Files going between the phone and the PC now, and the ones that just ended. */
    val transfers: StateFlow<List<com.sandevsystems.omarchyremote.network.Transfer>> get() = getApplication<KeypadApp>().files.transfers

    fun dismissTransfer(id: Int) = getApplication<KeypadApp>().files.dismiss(id)

    /** Opens a file that came from the PC in whatever app on the phone opens it. */
    fun openTransfer(t: com.sandevsystems.omarchyremote.network.Transfer) {
        val uri = t.uri ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, t.mime)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<KeypadApp>().startActivity(intent) }
            .onFailure { message = tr("Nenhum app do celular abre esse tipo de arquivo.", "No app on the phone opens this kind of file.") }
    }

    /** A link shared on the phone, opened in the PC's browser. */
    fun openUrlOnPc(text: String) {
        val url = com.sandevsystems.omarchyremote.network.firstLink(text) ?: return showMessage(tr("Não achei um link nesse texto.", "No link in that text."))
        if (transport != Transport.NETWORK || connection.value !is Connected) return showMessage(tr("Conecte-se pela rede para abrir no PC.", "Connect over the network to open it on the PC."))
        viewModelScope.launch {
            showMessage(if (network.openUrl(url)) tr("Aberto no navegador do PC.", "Opened in the PC's browser.") else tr("O PC não abriu o link.", "The PC didn't open the link."))
        }
    }

    /** The PC's Control Center. */
    val pcControls: StateFlow<com.sandevsystems.omarchyremote.network.Controls?> = network.controls

    fun loadControls() = network.controlsGet()

    fun setControl(id: String, value: Any? = null) {
        viewModelScope.launch { if (!network.pcControl(id, value)) showMessage(tr("O PC não fez isso agora.", "The PC didn't do that right now.")) }
    }

    /** Where a new agent can start. */
    val projects: StateFlow<List<com.sandevsystems.omarchyremote.network.Project>> = network.projects

    fun loadProjects() = network.listProjects()

    /** An agent being started (the sheet shows it), and the one that started (the tab opens it). */
    var startingAgent by mutableStateOf(false)
        private set
    var startedAgent by mutableStateOf<String?>(null)

    fun startAgent(cwd: String, kind: String, prompt: String) {
        if (startingAgent) return
        startingAgent = true
        viewModelScope.launch {
            val ok = network.startAgent(cwd, kind, prompt)
            startingAgent = false
            if (!ok) showMessage(tr("O agente não começou. Veja o terminal do herdr no PC.", "The agent didn't start. Check herdr's terminal on the PC."))
        }
    }

    /** Agents talking to each other (Mandar para…, reviews), and the notices waiting. */
    val agentLinks: StateFlow<com.sandevsystems.omarchyremote.network.AgentLinks> = network.links

    /** [text] (something [from] said) to the agent [to], with the person's [note] above it. */
    fun relayAgent(from: String, to: String, text: String, note: String?, toName: String, done: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = network.relayAgent(from, to, text, note?.takeIf { it.isNotBlank() })
            // A refusal comes with its reason (the ack's error); a silence says so here.
            if (ok) showMessage(tr("Enviado ao $toName.", "Sent to $toName."))
            else if (message == null) showMessage(tr("Não foi enviado ao $toName.", "It wasn't sent to $toName."))
            done(ok)
        }
    }

    /** [reviewer] ("claude" or "codex") of the same project reviews [agent]'s changes. */
    fun requestReview(agent: String, reviewer: String) {
        viewModelScope.launch {
            val name = com.sandevsystems.omarchyremote.network.AgentsText.kindName(reviewer)
            if (network.requestReview(agent, reviewer, if (com.sandevsystems.omarchyremote.ui.I18n.lang == com.sandevsystems.omarchyremote.ui.I18n.Lang.PT) "pt" else "en"))
                showMessage(tr("O $name está revisando. A revisão aparece aqui quando ficar pronta.", "$name is reviewing. The review shows up here when it's ready."))
        }
    }

    fun dismissNotice(id: String) = network.dismissNotice(id)

    /** The agents' recent sessions; a closed one can be reopened. */
    val sessions: StateFlow<List<com.sandevsystems.omarchyremote.network.RecentSession>> = network.sessions

    fun loadSessions() = network.listSessions()

    /** The session open again (its chat opens once it runs); an open one just opens. */
    fun resumeSession(session: com.sandevsystems.omarchyremote.network.RecentSession) {
        session.pane?.let { startedAgent = it; return }
        if (startingAgent) return
        startingAgent = true
        viewModelScope.launch {
            val ok = network.resumeSession(session.kind, session.id)
            startingAgent = false
            if (!ok) showMessage(tr("A sessão não reabriu. Veja o herdr no PC.", "The session didn't reopen. Check herdr on the PC."))
            else network.listSessions()
        }
    }

    /** The subagents of the open agent, as last read. */
    val agentSubagents: StateFlow<Pair<String, List<com.sandevsystems.omarchyremote.network.Subagent>>?> = network.agentSubagents

    fun loadSubagents(id: String) = network.agentSubagents(id)

    /** The agent's screen with its colors, for its terminal panel. */
    val agentScreen: StateFlow<Pair<String, String>?> = network.agentScreen

    fun readScreen(id: String) = network.readScreen(id)

    /** Commands already run in each agent from its suggestions (agent id → commands). */
    val ranCommands = androidx.compose.runtime.mutableStateMapOf<String, Set<String>>()

    /**
     * Runs commands in Claude Code's own session, in order: "! command" is its bash mode, so the
     * agent sees the output. The person confirmed them first.
     */
    fun runInAgent(agent: Agent, commands: List<String>) {
        viewModelScope.launch {
            for (command in commands) {
                if (!network.agentPrompt(agent.id, "! $command")) {
                    showMessage(tr("O agente não aceitou o comando agora.", "The agent didn't accept the command right now."))
                    return@launch
                }
                ranCommands[agent.id] = ranCommands[agent.id].orEmpty() + command
                delay(700)
            }
            showMessage(if (commands.size == 1) tr("Comando enviado ao agente.", "Command sent to the agent.") else tr("${commands.size} comandos enviados ao agente.", "${commands.size} commands sent to the agent."))
        }
    }

    /** Files on their way to an agent (the composer says so). */
    var agentUploads by mutableStateOf(0)
        private set

    /**
     * Files for the agent: into its project on the PC; each arrives as a mention for the message
     * (Claude Code reads "@path", files and images alike; others get the path).
     */
    fun attachToAgent(agent: Agent, uris: List<android.net.Uri>, onMention: (String) -> Unit) {
        if (uris.isEmpty()) return
        val url = videoUrl("") ?: return
        val files = getApplication<KeypadApp>().files
        agentUploads += uris.size
        viewModelScope.launch {
            for (uri in uris) {
                val ref = files.sendToAgent(uri, url.trimEnd('/'), secrets.pairingCode, agent.id)
                agentUploads--
                if (ref == null) showMessage(tr("O arquivo não chegou ao agente.", "The file didn't reach the agent."))
                else onMention(if (agent.kind == "claude") "@$ref" else ref)
            }
        }
    }

    private val imageClient by lazy { com.sandevsystems.omarchyremote.network.TailnetDns.client().build() }
    private val imageCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24)

    /**
     * An image the agent [agent] named in its chat (ImageRefs), from the PC (host images.py), no side
     * larger than [maxSide] px; null when it isn't there or can't go (not an image, hidden folder…).
     */
    suspend fun agentImage(agent: String, path: String, maxSide: Int): androidx.compose.ui.graphics.ImageBitmap? {
        val key = "$agent|$path|$maxSide"
        imageCache.get(key)?.let { return it }
        val base = prefs.getString("network_address", null)?.let(NetworkAddress::url)
        if (transport != Transport.NETWORK || connection.value !is Connected || base == null) return null
        val http = com.sandevsystems.omarchyremote.network.FileTransfer.httpUrl(base, "agent-image") ?: return null
        val url = runCatching {
            http.toHttpUrl().newBuilder().addQueryParameter("agent", agent).addQueryParameter("path", path).build()
        }.getOrNull() ?: return null
        val code = secrets.pairingCode
        val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder().url(url).header("X-Keypad-Token", code).build()
                val bytes = imageClient.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null } ?: return@runCatching null
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()
        } ?: return null
        imageCache.put(key, image)
        return image
    }

    private fun videoUrl(path: String): String? {
        val url = prefs.getString("network_address", null)?.let(NetworkAddress::url)
        if (transport != Transport.NETWORK || connection.value !is Connected || url == null) {
            message = tr("Isso usa a conexão pela rede (Tailscale). Conecte-se por Rede primeiro.", "This uses the network connection (Tailscale). Connect over Network first.")
            return null
        }
        return "$url/$path"
    }

    private fun openVideo(mode: VideoMode, url: String, onOpen: (VideoChannel) -> Unit, onText: (JSONObject) -> Unit) {
        releaseAll()
        videoMode = mode
        video.open(
            VideoChannel(
                url, secrets.pairingCode, onOpen, onText,
                onClosed = { reason ->
                    if (videoMode != VideoMode.NONE) message = reason
                    closeVideo()
                },
            ),
        )
    }

    private fun cursorFrom(message: JSONObject): Pair<Float, Float>? =
        if (message.optBoolean("inside", true)) message.getDouble("x").toFloat() to message.getDouble("y").toFloat() else null

    /** Asks the PC for a virtual monitor the size of this phone's screen and shows it full screen. */
    fun openDisplay(screenWidthPx: Int, screenHeightPx: Int) {
        val url = videoUrl("display") ?: return
        val (width, height, scale) = displayRequest(screenWidthPx, screenHeightPx)
        openVideo(
            VideoMode.DISPLAY, url,
            onOpen = { it.send(JSONObject().put("type", "display.start").put("width", width).put("height", height).put("scale", scale)) },
            onText = { if (it.optString("type") == "display") showVideo(it.getInt("width"), it.getInt("height")) },
        )
    }

    /** Shows one of the PC's monitors (the last one used, or the first) scaled to this phone's screen. */
    private var pendingWindow = false

    /** Ver PC straight into the focused window (the Tela tab's card). */
    fun openFocusedWindow(screenWidthPx: Int, screenHeightPx: Int) {
        openScreen(screenWidthPx, screenHeightPx)
        pendingWindow = videoMode == VideoMode.SCREEN  // set after: openScreen clears it
    }

    fun openScreen(screenWidthPx: Int, screenHeightPx: Int, monitor: String? = null) {
        // A plain Ver PC is a monitor: a window asked for before (and never shown) must not come back.
        pendingWindow = false
        val url = videoUrl("screen") ?: return
        screenLimit = maxOf(screenWidthPx, screenHeightPx) to minOf(screenWidthPx, screenHeightPx)
        openVideo(VideoMode.SCREEN, url, onOpen = {}, onText = { message ->
            when (message.optString("type")) {
                "monitors" -> {
                    val list = message.getJSONArray("monitors")
                    monitors = List(list.length()) { list.getJSONObject(it).getString("name") }
                    // Where the cursor is is where the person is working; the last monitor only when that is unknown.
                    val wanted = monitor ?: cursorAt.value?.monitor ?: prefs.getString("last_monitor", null)
                    val chosen = wanted?.takeIf { it in monitors } ?: monitors.firstOrNull() ?: return@openVideo
                    val quality = videoQuality
                    video.channel?.send(
                        JSONObject().put("type", "screen.start").put("monitor", chosen).put("fps", quality.fps)
                            .put("maxWidth", screenLimit.first).put("maxHeight", screenLimit.second).put("scale", quality.scale.toDouble()),
                    )
                    startStats()
                }
                "cursor" -> {
                    cursor = cursorFrom(message)
                    // The cursor crossed to another monitor: the view goes with it.
                    followMonitor(message.optString("monitor").ifEmpty { null }, currentMonitor, monitors, followingTo)?.let {
                        followingTo = it
                        switchMonitor(it)
                    }
                }
                "video.pong" -> videoStats.pong(message.optLong("t"), SystemClock.elapsedRealtime())
                "screen" -> {
                    if (pendingWindow && !message.has("window")) {
                        pendingWindow = false
                        showFocusedWindow()
                    }
                    followingTo = null
                    currentMonitor = message.getString("monitor")
                    focusedWindow = message.optString("window").ifEmpty { null }
                    prefs.edit { putString("last_monitor", currentMonitor) }
                    showVideo(message.getInt("width"), message.getInt("height"))
                }
            }
        })
    }

    var videoQuality by mutableStateOf(VideoQuality.from(prefs.getString("video_quality", null)))
        private set
    /** "42 ms · 58 qps" for the Ver PC chip; empty until measured. */
    var videoStatsText by mutableStateOf("")
        private set
    private val videoStats = VideoStats()
    private var statsJob: Job? = null

    fun changeVideoQuality(quality: VideoQuality) {
        videoQuality = quality
        prefs.edit { putString("video_quality", quality.name) }
        if (videoMode == VideoMode.SCREEN) {
            video.channel?.send(JSONObject().put("type", "screen.quality").put("fps", quality.fps).put("scale", quality.scale.toDouble()))
        }
    }

    /** Round trip every 2 s and decoded frames every second while the PC's screen is shown. */
    private fun startStats() {
        statsJob?.cancel()
        videoStats.reset()
        videoStatsText = ""
        statsJob = viewModelScope.launch {
            var lastRendered = video.rendered()
            var tick = 0
            while (true) {
                delay(1_000)
                val now = SystemClock.elapsedRealtime()
                val rendered = video.rendered()
                repeat((rendered - lastRendered).coerceIn(0, 120).toInt()) { videoStats.frame(now) }
                lastRendered = rendered
                if (tick++ % 2 == 0) video.channel?.send(JSONObject().put("type", "video.ping").put("t", now))
                videoStatsText = videoStats.text(now)
            }
        }
    }

    /** Monitor Ver PC is switching to because the cursor went there, until its stream starts. */
    private var followingTo: String? = null

    /** Another monitor on the same connection: only the PC's capture restarts. */
    /** Only the focused window, full screen on the phone; the PC keeps it following the focus. */
    fun showFocusedWindow() {
        if (videoMode != VideoMode.SCREEN) return
        video.channel?.send(JSONObject().put("type", "screen.switch").put("window", true))
    }

    fun switchMonitor(name: String) {
        if (name == currentMonitor && focusedWindow == null || videoMode != VideoMode.SCREEN) return
        val channel = video.channel
        if (channel == null) {
            val (width, height) = screenLimit
            closeVideo()
            openScreen(width, height, name)
            return
        }
        followingTo = name
        channel.send(JSONObject().put("type", "screen.switch").put("monitor", name))
    }

    fun attachVideoSurface(surface: Surface) = video.attach(surface)

    fun detachVideoSurface() = video.detach()

    /** Touch on the video, normalized to the monitor: down/move/up (left button), right, scroll. */
    fun videoTouch(action: String, x: Float, y: Float, steps: Int = 0) {
        if (videoSize == null) return  // not streaming yet (e.g. mid-switch): the PC would take it as the request
        val message = JSONObject().put("type", "touch").put("action", action).put("x", x.toDouble()).put("y", y.toDouble())
        if (action == "scroll") message.put("steps", steps)
        video.channel?.send(message)
    }

    /** The S Pen on Ver PC: hover/down/move/up/out at a point of the monitor (0..1), pressure 0..1. */
    fun pen(state: String, x: Float, y: Float, pressure: Float) {
        if (videoSize == null || videoMode != VideoMode.SCREEN) return
        video.channel?.send(
            JSONObject().put("type", "pen").put("state", state).put("x", x.coerceIn(0f, 1f).toDouble())
                .put("y", y.coerceIn(0f, 1f).toDouble()).put("pressure", pressure.coerceIn(0f, 1f).toDouble()),
        )
    }

    /** Touchpad mode on the video: rel (dx, dy as fractions of the monitor), click, press, release, scroll, hscroll. */
    fun videoPointer(action: String, dx: Float = 0f, dy: Float = 0f, value: Int = 0) {
        if (videoSize == null) return  // not streaming yet (e.g. mid-switch)
        val message = JSONObject().put("type", "pointer").put("action", action)
        when (action) {
            "rel" -> message.put("dx", dx.coerceIn(-1f, 1f).toDouble()).put("dy", dy.coerceIn(-1f, 1f).toDouble())
            "click" -> message.put("button", value)
            "scroll", "hscroll" -> message.put("steps", value.coerceIn(-20, 20))
        }
        video.channel?.send(message)
    }

    fun toggleVideoHold() {
        videoHold = !videoHold
        videoPointer(if (videoHold) "press" else "release")
    }

    fun changeVideoTouchpad(value: Boolean) {
        videoTouchpad = value
        prefs.edit { putBoolean("video_touchpad", value) }
    }

    fun changeNaturalScroll(value: Boolean) {
        naturalScroll = value
        prefs.edit { putBoolean("natural_scroll", value) }
    }

    fun changeTapToClick(value: Boolean) {
        tapToClick = value
        prefs.edit { putBoolean("tap_to_click", value) }
    }

    fun markHintsSeen() {
        hintsSeen = true
        prefs.edit { putBoolean("hints_seen", true) }
    }

    fun showHints() {
        hintsSeen = false
    }

    /** Over the network the host switches directly; over Bluetooth the Omarchy shortcut is typed. */
    fun goToWorkspace(id: Int) {
        val session = network.input
        if (transport == Transport.NETWORK && session != null) session.goToWorkspace(id)
        else if (id in Workspaces.numbers) pressShortcut(Workspaces.switchTo(id))
    }

    fun stepWorkspace(direction: Int) {
        val session = network.input
        if (transport == Transport.NETWORK && session != null) session.stepWorkspace(direction)
        else pressShortcut(if (direction > 0) Workspaces.next else Workspaces.previous)
    }

    /** Closing the stream stops the capture; for the extra monitor the host also removes it. */
    fun closeVideo() {
        statsJob?.cancel()
        statsJob = null
        videoStatsText = ""
        videoMode = VideoMode.NONE
        videoHold = false
        cursor = null
        monitors = emptyList()
        currentMonitor = null
        focusedWindow = null
        video.close()
        videoSize = null
    }

    private fun showVideo(width: Int, height: Int) {
        videoSize = width to height
        video.setSize(width, height)
    }

    fun move(dx: Float, dy: Float) {
        val (x, y) = pointer.add(dx * sensitivity, dy * sensitivity)
        if (x != 0 || y != 0) input?.movePointer(x, y)
    }

    /** Natural scrolling: fingers moving up scroll the page down, as on the phone. */
    fun scroll(dy: Float) {
        val steps = wheel.add(dy)
        if (steps != 0) input?.scroll(steps)
    }

    fun click(button: Int) {
        val input = input ?: return
        val held = if (dragging) MouseButtons.LEFT else 0
        clickJob = viewModelScope.launch {
            input.setMouseButtons(held or button)
            delay(CLICK_MS)
            // Read the drag state again: it may have ended during the click.
            input.setMouseButtons(if (dragging) MouseButtons.LEFT else 0)
        }
    }

    /** Whole wheel steps from the touchpad gesture (already in the chosen direction). */
    fun scrollSteps(steps: Int) {
        if (steps != 0) input?.scroll(steps)
    }

    /** Double tap and hold on the touchpad: left button down until the finger lifts. */
    fun setDrag(on: Boolean) {
        val input = input ?: return
        if (dragging == on) return
        dragging = on
        input.setMouseButtons(if (on) MouseButtons.LEFT else 0)
    }

    fun toggleDrag() {
        val input = input ?: return
        dragging = !dragging
        input.setMouseButtons(if (dragging) MouseButtons.LEFT else 0)
    }

    /** "Soltar": clears the selected modifiers without sending a key. */
    fun releaseModifiers() {
        modifiers = 0
    }

    fun toggleModifier(modifier: Int) {
        modifiers = modifiers xor modifier
    }

    /** A key with the selected modifiers; they apply to this key and are then cleared ("vale para a próxima tecla"). */
    fun pressKey(usage: Int) {
        val input = input ?: return
        val key = KeyStroke(usage, modifiers)
        modifiers = 0
        viewModelScope.launch {
            if (!input.tapKey(key)) message = tr("A tecla não foi enviada.", "The key wasn't sent.")
        }
    }

    /** A complete shortcut (e.g. Ctrl+C); the latched modifiers are neither added nor released. */
    /** Set by the screen: whether a hardware keyboard on the phone types on the PC right now (no text field open). */
    var hardwareKeysToPc by mutableStateOf(false)

    /** A key from a keyboard attached to the phone; true when it went to the PC (or is a modifier held for it). */
    fun hardwareKey(keyCode: Int, metaState: Int): Boolean {
        if (!hardwareKeysToPc || input == null) return false
        if (keyCode in MODIFIER_KEYCODES) return true  // it only changes the next key
        val stroke = HardwareKeys.stroke(keyCode, metaState) ?: return false
        pressShortcut(stroke)
        return true
    }

    /** A shortcut chip: copy, paste and cut through Omarchy's universal clipboard over the network. */
    fun runShortcut(shortcut: com.sandevsystems.omarchyremote.input.Shortcut) {
        when (com.sandevsystems.omarchyremote.input.Shortcuts.clipboardAction(shortcut.id, transport == Transport.NETWORK)) {
            "copy" -> copyOnPc()
            "paste" -> pasteOnPc()
            "cut" -> cutOnPc()
            else -> pressShortcut(shortcut.key)
        }
    }

    fun pressShortcut(key: KeyStroke) {
        val input = input ?: return
        viewModelScope.launch {
            if (!input.tapKey(key)) message = tr("O atalho não foi enviado.", "The shortcut wasn't sent.")
        }
    }

    /** Keys typed live from Ver PC's "Digitar" bar, delivered strictly in order. */
    private val liveKeys = kotlinx.coroutines.channels.Channel<List<KeyStroke>>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (keys in liveKeys) {
                val input = input ?: continue
                if (input.type(keys) < keys.size) message = tr("Parte do texto não chegou ao PC.", "Part of the text didn't reach the PC.")
            }
        }
    }

    /** A change in the "Digitar" field: backspaces for what was removed, then the new text. */
    fun typeLive(change: TypingDiff.Change) {
        val keys = MutableList(change.backspaces) { KeyStroke(USAGE_BACKSPACE) }
        if (change.insert.isNotEmpty()) {
            when (val encoded = layout.encode(change.insert)) {
                is Encoding.Unsupported -> showMessage(tr("\"${encoded.character}\" não existe no layout ${layout.label}.", "\"${encoded.character}\" isn't in the ${layout.label} layout."))
                is Encoding.Keys -> keys += encoded.keys
            }
        }
        if (keys.isNotEmpty()) liveKeys.trySend(keys)
    }

    /** One character as a shortcut (e.g. Ctrl+C from the "Digitar" bar with Ctrl latched). */
    fun typeShortcut(character: String, modifiers: Int) {
        when (val encoded = layout.encode(character)) {
            is Encoding.Keys -> encoded.keys.singleOrNull()?.let { liveKeys.trySend(listOf(it.copy(modifiers = it.modifiers or modifiers))) }
            is Encoding.Unsupported -> Unit
        }
    }

    /** A special key from the "Digitar" bar (Esc, Tab, arrows, Enter…), in order with the typed text. */
    fun typeKey(usage: Int, modifiers: Int = 0) {
        liveKeys.trySend(listOf(KeyStroke(usage, modifiers)))
    }

    fun sendText() {
        val input = input ?: return
        if (transport == Transport.BLUETOOTH && capsLock.value) {
            message = tr("Caps Lock está ativo no computador. Desative-o antes de enviar texto.", "Caps Lock is on at the computer. Turn it off before sending text.")
            return
        }
        val keys = when (val encoded = layout.encode(draft)) {
            is Encoding.Unsupported -> {
                message = tr("\"${encoded.character}\" não pode ser digitado no layout ${layout.label}. Nada foi enviado.", "\"${encoded.character}\" can't be typed in the ${layout.label} layout. Nothing was sent.")
                return
            }
            is Encoding.Keys -> encoded.keys
        }
        message = null
        typing = true
        typingJob = viewModelScope.launch {
            try {
                val sent = input.type(keys)
                if (sent == keys.size) draft = ""
                else message = tr("Envio incompleto: $sent de ${keys.size} teclas. O texto foi mantido e não será reenviado.", "Incomplete send: $sent of ${keys.size} keys. The text was kept and won't be resent.")
            } finally {
                typing = false
            }
        }
    }

    fun cancelTyping() {
        typingJob?.cancel()
        typingJob = null
    }

    fun setShortcutSlot(index: Int, id: String) {
        val ids = shortcutSlots.map { it.id }.toMutableList().also { it[index] = id }
        prefs.edit { putString("shortcut_slots", ids.joinToString(",")) }
        shortcutSlots = ids.map(Shortcuts::byId)
    }

    fun changeLayout(value: KeyboardLayout) {
        layout = value
        prefs.edit { putString("layout", value.name) }
    }

    fun changeSensitivity(value: Float) {
        sensitivity = value
        prefs.edit { putFloat("sensitivity", value) }
    }

    /** A short notice for the floating message (design §4.9). */
    fun showMessage(text: String) {
        messageState = text
        messageIsError = false
    }

    fun dismissMessage() {
        message = null
    }

    /** Stops typing and releases every key and button, while the connection still exists. */
    fun releaseAll() {
        cancelTyping()
        input?.releaseAll()
        resetControls()
    }

    private fun resetControls() {
        cancelTyping()
        clickJob?.cancel()
        dragging = false
        modifiers = 0
        pointer.reset()
        wheel.reset()
    }

    override fun onCleared() {
        closeVideo()
        if (!followAgents) network.disconnect()
        bluetooth.close()
    }

    companion object {
        /** Messages per page of an agent's conversation: a screenful and a bit, a few KB. */
        const val CHAT_PAGE = 60
        /** CTRL_LEFT/RIGHT, SHIFT_LEFT/RIGHT, ALT_LEFT/RIGHT, META_LEFT/RIGHT (android.view.KeyEvent). */
        private val MODIFIER_KEYCODES = setOf(113, 114, 59, 60, 57, 58, 117, 118)
        const val SCROLL_STEP_DP = 18f
        private const val CLICK_MS = 20L
        /** HID usages of the arrow keys: a presenter's next/previous slide. */
        private const val USAGE_RIGHT = 0x4F
        private const val USAGE_LEFT = 0x50
        private const val USAGE_BACKSPACE = 0x2A
    }
}
