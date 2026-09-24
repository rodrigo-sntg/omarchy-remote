package com.sandevsystems.omarchyremote.network

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.sandevsystems.omarchyremote.ConnectionState
import com.sandevsystems.omarchyremote.ConnectionState.Connected
import com.sandevsystems.omarchyremote.ConnectionState.Connecting
import com.sandevsystems.omarchyremote.ConnectionState.Disconnected
import com.sandevsystems.omarchyremote.ConnectionState.Error
import com.sandevsystems.omarchyremote.ConnectionState.Ready
import com.sandevsystems.omarchyremote.Host
import com.sandevsystems.omarchyremote.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Input over Tailscale to host/keypad_host. OkHttp callbacks are posted to the main thread,
 * where all state lives; each connection gets a fresh [NetworkSession], so nothing is replayed.
 */
class NetworkController(private val scope: CoroutineScope) {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<ConnectionState>(Ready)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    /** The PC's workspaces, live while connected. */
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()
    private val _monitors = MutableStateFlow<List<MonitorInfo>>(emptyList())
    /** The PC's monitor layout, live while connected (for the cursor map). */
    val monitors: StateFlow<List<MonitorInfo>> = _monitors.asStateFlow()
    private val _cursor = MutableStateFlow<CursorAt?>(null)
    /** Where the PC's cursor is, up to 15 times per second while connected. */
    val cursor: StateFlow<CursorAt?> = _cursor.asStateFlow()
    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    /** The herdr agents on the PC, live while connected. */
    val agents: StateFlow<List<Agent>> = _agents.asStateFlow()
    // Until the host says otherwise (hosts without herdr say nothing), herdr counts as unavailable.
    private val _herdrAvailable = MutableStateFlow(false)
    val herdrAvailable: StateFlow<Boolean> = _herdrAvailable.asStateFlow()
    private val _alerts = MutableSharedFlow<Agent>(extraBufferCapacity = 16)
    /** An agent that just became blocked or done. */
    val alerts: SharedFlow<Agent> = _alerts.asSharedFlow()
    private val _agentText = MutableStateFlow<Pair<String, String>?>(null)
    /** The last terminal text read from an agent: id to text. */
    val agentText: StateFlow<Pair<String, String>?> = _agentText.asStateFlow()
    private val _agentPages = MutableSharedFlow<AgentPage>(extraBufferCapacity = 8)
    /** Pages of an agent's conversation, as asked with [agentHistory]. */
    val agentPages: SharedFlow<AgentPage> = _agentPages.asSharedFlow()
    private val _agentItems = MutableSharedFlow<Triple<String, List<ChatItem>, Long>>(extraBufferCapacity = 64)
    /** The followed agent's new messages: id, messages, where its file now ends. */
    val agentItems: SharedFlow<Triple<String, List<ChatItem>, Long>> = _agentItems.asSharedFlow()
    private val _agentGit = MutableStateFlow<Pair<String, GitSummary>?>(null)
    val agentGit: StateFlow<Pair<String, GitSummary>?> = _agentGit.asStateFlow()
    private val _agentGitDiff = MutableStateFlow<Triple<String, String, String>?>(null)
    val agentGitDiff: StateFlow<Triple<String, String, String>?> = _agentGitDiff.asStateFlow()
    private val _thumbs = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 8)
    /** Monitor thumbnails as they arrive: monitor, JPEG. */
    val thumbs: SharedFlow<Pair<String, ByteArray>> = _thumbs.asSharedFlow()
    private val _stats = MutableStateFlow<PcStats?>(null)
    val stats: StateFlow<PcStats?> = _stats.asStateFlow()
    private val _host = MutableStateFlow<HostInfo?>(null)
    /** The PC service's version (and whether new code waits). */
    val host: StateFlow<HostInfo?> = _host.asStateFlow()
    private val _files = MutableStateFlow<FolderListing?>(null)
    /** The PC folder last listed. */
    val files: StateFlow<FolderListing?> = _files.asStateFlow()
    private val _controls = MutableStateFlow<Controls?>(null)
    /** The PC's Control Center, as last read. */
    val controls: StateFlow<Controls?> = _controls.asStateFlow()
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    /** Where an agent can be started (herdr's open projects, then Claude's recent ones). */
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()
    private val _agentStarted = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** The pane of an agent that just started. */
    val agentStarted: SharedFlow<String> = _agentStarted.asSharedFlow()
    private val _agentSubagents = MutableStateFlow<Pair<String, List<Subagent>>?>(null)
    /** The last subagents read of an agent. */
    val agentSubagents: StateFlow<Pair<String, List<Subagent>>?> = _agentSubagents.asStateFlow()
    private val _agentScreen = MutableStateFlow<Pair<String, String>?>(null)
    /** The last colored screen read of an agent: id to ANSI text. */
    val agentScreen: StateFlow<Pair<String, String>?> = _agentScreen.asStateFlow()
    private val _agentCommands = MutableSharedFlow<Triple<String, String, List<SlashCommand>>>(extraBufferCapacity = 4)
    /** An agent's "/" menu: agent, kind, entries. */
    val agentCommands: SharedFlow<Triple<String, String, List<SlashCommand>>> = _agentCommands.asSharedFlow()
    private val _agentOutputs = MutableSharedFlow<Triple<String, String, String>>(extraBufferCapacity = 8)
    /** An action's output fetched whole: agent, call, text. */
    val agentOutputs: SharedFlow<Triple<String, String, String>> = _agentOutputs.asSharedFlow()
    private val _theme = MutableStateFlow<Pair<String, Map<String, String>>?>(null)
    /** The PC's Omarchy theme (mode, colors), as last received. */
    val theme: StateFlow<Pair<String, Map<String, String>>?> = _theme.asStateFlow()
    private val _clipboard = MutableSharedFlow<PcClip>(extraBufferCapacity = 4)
    /** Text the PC sent to the phone's clipboard (asked for, its Phone menu, or a copy made there). */
    val clipboard: SharedFlow<PcClip> = _clipboard.asSharedFlow()
    private val _fileOffers = MutableSharedFlow<FileOffer>(extraBufferCapacity = 8)
    /** Files and prints the PC offers (file.offer). */
    val fileOffers: SharedFlow<FileOffer> = _fileOffers.asSharedFlow()
    private val _pcNotifications = MutableSharedFlow<PcNotification>(extraBufferCapacity = 16)
    val pcNotifications: SharedFlow<PcNotification> = _pcNotifications.asSharedFlow()
    private val _opens = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** "screen", "extra" or "terminal": the PC asked the phone to open it. */
    val opens: SharedFlow<String> = _opens.asSharedFlow()
    private val _pcLock = MutableStateFlow<PcLock?>(null)
    /** The PC's lock screen (its service pushes each change). */
    val pcLock: StateFlow<PcLock?> = _pcLock.asStateFlow()
    private val _unlockReplies = MutableSharedFlow<UnlockReply>(extraBufferCapacity = 8)
    val unlockReplies: SharedFlow<UnlockReply> = _unlockReplies.asSharedFlow()
    private val _phoneReplies = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    /** The PC answered one of the phone's notifications: (key, text). */
    val phoneReplies: SharedFlow<Pair<String, String>> = _phoneReplies.asSharedFlow()
    private val _phoneDismisses = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** The PC dismissed one of the phone's notifications. */
    val phoneDismisses: SharedFlow<String> = _phoneDismisses.asSharedFlow()
    private val _media = MutableStateFlow<PcMedia?>(null)
    /** What plays on the PC (pushed by it as it changes); null until it says. */
    val media: StateFlow<PcMedia?> = _media.asStateFlow()
    private val _rings = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    /** The PC asked the phone to ring (true) or to stop (false). */
    val rings: SharedFlow<Boolean> = _rings.asSharedFlow()
    private val _usage = MutableStateFlow<UsageState?>(null)
    val usage: StateFlow<UsageState?> = _usage.asStateFlow()
    private val _now = MutableStateFlow<NowState?>(null)
    val now: StateFlow<NowState?> = _now.asStateFlow()
    private val _binds = MutableStateFlow<List<Bind>>(emptyList())
    /** Omarchy's keybindings, once asked for. */
    val binds: StateFlow<List<Bind>> = _binds.asStateFlow()
    private val _windows = MutableStateFlow<List<PcWindow>>(emptyList())
    /** The PC's windows, once asked for (and after each window action). */
    val windows: StateFlow<List<PcWindow>> = _windows.asStateFlow()
    private val _menu = MutableStateFlow<OmarchyMenu?>(null)
    /** Omarchy's Super+Space menu and apps, once asked for. */
    val menu: StateFlow<OmarchyMenu?> = _menu.asStateFlow()
    /** Set by the ViewModel: an error the host attached to an ack (shown as a message). */
    var onSoftError: (String) -> Unit = {}

    private var socket: WebSocket? = null
    private var session: NetworkSession? = null
    private var heartbeat: Job? = null
    private var sessionTimeout: Job? = null

    /** Input for the current connection, once the host opened a session. */
    val input: NetworkSession? get() = session?.takeIf { it.isOpen }

    fun connect(address: String, pairingCode: String) {
        val url = NetworkAddress.url(address)
        if (url == null) {
            _state.value = Error(tr("Esse não parece o endereço do PC. O jeito mais fácil: ler o QR do PC.", "That doesn't look like the PC's address. The easy way: scan the PC's QR code."))
            return
        }
        if (pairingCode.isBlank()) {
            _state.value = Error(tr("Falta o código de pareamento. Leia o QR do PC.", "The pairing code is missing. Scan the PC's QR code."))
            return
        }
        close()
        val host = Host(name = address.trim().substringBefore('.'), address = address.trim(), isComputer = true)
        _state.value = Connecting(host)
        var hostError: String? = null
        lateinit var ws: WebSocket
        val session = NetworkSession(
            onError = { hostError = it },
            onWorkspaces = { list -> main.post { if (this.session?.isOpen == true) _workspaces.value = list } },
            onMonitors = { list -> main.post { if (this.session?.isOpen == true) _monitors.value = list } },
            onCursor = { at -> main.post { if (this.session?.isOpen == true) _cursor.value = at } },
            onAgents = { list, available ->
                main.post {
                    if (this.session?.isOpen == true) {
                        _agents.value = list
                        _herdrAvailable.value = available
                    }
                }
            },
            onAgentAlert = { agent -> main.post { _alerts.tryEmit(agent) } },
            onAgentText = { id, text -> main.post { _agentText.value = id to text } },
            onAgentPage = { page -> main.post { _agentPages.tryEmit(page) } },
            onAgentItems = { id, items, end -> main.post { _agentItems.tryEmit(Triple(id, items, end)) } },
            onAgentOutput = { id, call, text -> main.post { _agentOutputs.tryEmit(Triple(id, call, text)) } },
            onAgentGit = { id, g -> main.post { _agentGit.value = id to g } },
            onAgentGitDiff = { id, path, diff -> main.post { _agentGitDiff.value = Triple(id, path, diff) } },
            onThumb = { m, jpeg -> main.post { _thumbs.tryEmit(m to jpeg) } },
            onStats = { st -> main.post { _stats.value = st } },
            onHost = { h -> main.post { _host.value = h } },
            onFiles = { f -> main.post { _files.value = f } },
            onControls = { c -> main.post { _controls.value = c } },
            onProjects = { list -> main.post { _projects.value = list } },
            onAgentStarted = { id -> main.post { _agentStarted.tryEmit(id) } },
            onAgentSubagents = { id, items -> main.post { _agentSubagents.value = id to items } },
            onAgentScreen = { id, text -> main.post { _agentScreen.value = id to text } },
            onAgentCommands = { id, kind, items -> main.post { _agentCommands.tryEmit(Triple(id, kind, items)) } },
            onSoftError = { text -> main.post { onSoftError(text) } },
            onTheme = { mode, colors -> main.post { _theme.value = mode to colors } },
            onClipboard = { clip -> main.post { _clipboard.tryEmit(clip) } },
            onOpen = { what -> main.post { _opens.tryEmit(what) } },
            onRing = { on -> main.post { _rings.tryEmit(on) } },
            onMedia = { m -> main.post { _media.value = m } },
            onPcLock = { l -> main.post { _pcLock.value = l } },
            onUnlock = { r -> main.post { _unlockReplies.tryEmit(r) } },
            onPhoneReply = { k, t -> main.post { _phoneReplies.tryEmit(k to t) } },
            onPhoneDismiss = { k -> main.post { _phoneDismisses.tryEmit(k) } },
            onMenu = { menu -> main.post { _menu.value = menu } },
            onBinds = { list -> main.post { _binds.value = list } },
            onNow = { state -> main.post { _now.value = state } },
            onUsage = { state -> main.post { _usage.value = state } },
            onWindows = { list -> main.post { _windows.value = list } },
            onFileOffer = { offer -> main.post { _fileOffers.tryEmit(offer) } },
            onPcNotification = { n -> main.post { _pcNotifications.tryEmit(n) } },
        ) { text -> ws.send(text) }
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = onMain(webSocket) {
                session.onMessage(text, SystemClock.elapsedRealtime())
                if (session.isOpen && _state.value !is Connected) {
                    Log.i(TAG, "connected to $url")
                    sessionTimeout?.cancel()
                    _state.value = Connected(host)
                    startHeartbeat(session)
                }
            }

            // OkHttp only reports a close started by the host through onClosing; answer it and drop.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onMain(webSocket) { drop(hostError ?: tr("O PC encerrou a conexão.", "The PC closed the connection.")) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onMain(webSocket) {
                drop(hostError ?: tr("O PC encerrou a conexão.", "The PC closed the connection."))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onMain(webSocket) {
                Log.w(TAG, "failure ${response?.code}: $t")
                drop(
                    when {
                        response?.code == 403 ->
                            tr("O PC não reconheceu este celular. Pareie de novo pelo QR.", "The PC didn't recognize this phone. Pair again with the QR code.")
                        hostError != null -> hostError!!
                        else -> tr("Não achei o ${host.name}. Ele está ligado, e o Tailscale ativo no PC e no celular?", "Can't find ${host.name}. Is it on, with Tailscale running on the PC and the phone?")
                    },
                )
            }
        }
        val request = Request.Builder().url(url).header(PAIRING_HEADER, pairingCode.trim()).build()
        ws = client.newWebSocket(request, listener)
        socket = ws
        this.session = session
        sessionTimeout = scope.launch {
            delay(SESSION_TIMEOUT_MS)
            if (socket === ws && !session.isOpen) drop(tr("O PC não respondeu. O Omarchy Remote está rodando nele?", "The PC didn't answer. Is Omarchy Remote running on it?"))
        }
    }

    /** Releases input on the host, then closes; the state goes back to idle. */
    suspend fun clipboardSet(text: String) = input?.clipboardSet(text) ?: false

    fun clipboardGet() {
        input?.clipboardGet()
    }

    fun nowGet() {
        input?.nowGet()
    }

    suspend fun mediaCmd(action: String) = input?.mediaCmd(action) ?: false

    fun phoneNotification(key: String, app: String, title: String, text: String, reply: Boolean) =
        input?.phoneNotification(key, app, title, text, reply)

    fun phoneNotificationRemoved(key: String) = input?.phoneNotificationRemoved(key)

    fun unlockEnroll(publicKey: String) = input?.unlockEnroll(publicKey)
    fun unlockChallenge() = input?.unlockChallenge()
    fun unlockRespond(signature: String) = input?.unlockRespond(signature)

    suspend fun pcAct(action: String) = input?.pcAct(action) ?: false

    fun usageGet(force: Boolean = false) {
        input?.usageGet(force)
    }

    suspend fun shotGet(monitor: String?, region: FloatArray?, tag: String) = input?.shotGet(monitor, region, tag) ?: false

    fun bindsGet() {
        input?.bindsGet()
    }

    fun windowsGet() {
        input?.windowsGet()
    }

    suspend fun windowAct(address: String, action: String, workspace: Int? = null) = input?.windowAct(address, action, workspace) ?: false

    fun menuGet() {
        input?.menuGet()
    }

    suspend fun menuRun(id: String) = input?.menuRun(id) ?: false

    suspend fun menuShow(route: String) = input?.menuShow(route) ?: false

    fun watchCursor(on: Boolean) {
        input?.watch(on)
    }

    fun phoneStatus(battery: Int, charging: Boolean) {
        input?.phoneStatus(battery, charging)
    }

    fun readAgent(id: String, lines: Int = 40) {
        input?.readAgent(id, lines)
    }

    fun agentHistory(id: String, before: Long?, limit: Int) {
        input?.agentHistory(id, before, limit)
    }

    fun agentFollow(id: String, after: Long) {
        input?.agentFollow(id, after)
    }

    fun agentUnfollow() {
        input?.agentUnfollow()
    }

    fun listProjects() {
        input?.listProjects()
    }

    fun controlsGet() {
        input?.controlsGet()
    }

    fun hostGet() {
        input?.hostGet()
    }

    fun statsGet() {
        input?.statsGet()
    }

    fun thumbGet(monitor: String, width: Int) {
        input?.thumbGet(monitor, width)
    }

    fun agentGit(id: String) {
        input?.agentGit(id)
    }

    fun agentGitDiff(id: String, path: String) {
        input?.agentGitDiff(id, path)
    }

    suspend fun hostRestart() = input?.hostRestart() ?: false

    fun filesList(path: String?) {
        input?.filesList(path)
    }

    suspend fun filesFetch(path: String) = input?.filesFetch(path) ?: false

    suspend fun openUrl(url: String) = input?.openUrl(url) ?: false

    suspend fun pcControl(id: String, value: Any? = null) = input?.pcControl(id, value) ?: false

    suspend fun startAgent(cwd: String, kind: String, prompt: String) = input?.startAgent(cwd, kind, prompt) ?: false

    fun agentSubagents(id: String) {
        input?.agentSubagents(id)
    }

    fun readScreen(id: String) {
        input?.readScreen(id)
    }

    fun agentCommands(id: String) {
        input?.agentCommands(id)
    }

    fun agentOutput(id: String, at: Long, call: String) {
        input?.agentOutput(id, at, call)
    }

    suspend fun agentKeys(id: String, keys: List<String>) = input?.agentKeys(id, keys) ?: false

    suspend fun agentPrompt(id: String, text: String) = input?.agentPrompt(id, text) ?: false

    suspend fun focusAgent(id: String) = input?.focusAgent(id) ?: false

    fun disconnect() {
        close()
        _state.value = Ready
    }

    private fun startHeartbeat(session: NetworkSession) {
        heartbeat = scope.launch {
            while (true) {
                delay(NetworkSession.PING_INTERVAL_MS)
                session.ping()
                if (session.isExpired(SystemClock.elapsedRealtime())) {
                    drop(tr("O PC parou de responder.", "The PC stopped responding."))
                    return@launch
                }
            }
        }
    }

    private fun drop(reason: String) {
        close()
        _state.value = Disconnected(reason)
    }

    private fun close() {
        _workspaces.value = emptyList()
        _monitors.value = emptyList()
        _cursor.value = null
        _agents.value = emptyList()
        _herdrAvailable.value = false
        _agentText.value = null
        heartbeat?.cancel()
        heartbeat = null
        sessionTimeout?.cancel()
        sessionTimeout = null
        session?.close()
        session = null
        socket?.close(1000, null)
        socket = null
    }

    /** Runs [block] on the main thread, ignoring events from a socket that was already replaced. */
    private fun onMain(webSocket: WebSocket, block: () -> Unit) {
        main.post { if (webSocket === socket) block() }
    }

    companion object {
        private const val TAG = "KeypadNet"
        private const val PAIRING_HEADER = "X-Keypad-Token"
        private const val SESSION_TIMEOUT_MS = 5_000L
    }
}
