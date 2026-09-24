package com.sandevsystems.omarchyremote.network

import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.RemoteInput
import com.sandevsystems.omarchyremote.ui.tr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol v1 client for one WebSocket connection (see host/keypad_host/protocol.py).
 * Not thread-safe: the owner calls everything on the main thread. After [close] nothing is sent.
 */
/** A workspace as the host reports it (see host/keypad_host/workspaces.py). */
data class Workspace(val id: Int, val monitor: String, val windows: Int, val active: Boolean, val focused: Boolean)

/** A PC monitor in the compositor's logical layout; [extra] = the phone's virtual monitor. */
data class MonitorInfo(val name: String, val x: Int, val y: Int, val width: Int, val height: Int, val workspace: Int, val extra: Boolean)

/** Where the PC's cursor is: monitor and position on it (0..1). */
data class CursorAt(val monitor: String, val x: Float, val y: Float)

/** A herdr agent as the host reports it (host/keypad_host/agents.py). status: idle/working/blocked/done/unknown. */
data class Agent(
    val id: String, val kind: String, val status: String, val title: String, val workspace: String, val cwd: String, val seq: Int,
    /** Subagents it launched that are running now (Claude Code; the PC counts them). */
    val subagents: Int = 0,
)

/** An agent's project in git: the branch, what changed, the last commits, ahead/behind its upstream. */
data class GitSummary(val repo: Boolean, val branch: String, val files: List<GitFile>, val commits: List<GitCommit>, val ahead: Int, val behind: Int)
data class GitFile(val path: String, val state: String, val added: Int, val removed: Int)
data class GitCommit(val hash: String, val `when`: String, val subject: String)

/** How the PC is doing: CPU %, memory in GB, CPU temperature, a laptop's battery, seconds since boot. */
data class PcStats(val cpu: Int, val memUsed: Double, val memTotal: Double, val temp: Int?, val battery: Int?, val charging: Boolean, val uptime: Long)

/** The PC service's code: its fingerprint, and whether newer code waits for a restart. */
/** The PC's lock screen, as its service watches it; unlocking needs the key enrolled and the lock's rule. */
data class PcLock(val locked: Boolean, val enrolled: Boolean, val rule: Boolean)

/** A reply about unlocking: a challenge (its nonce), the result, or the enrollment. */
data class UnlockReply(val type: String, val ok: Boolean, val reason: String?, val nonce: String?)

data class HostInfo(val version: String, val outdated: Boolean, val wake: List<Wake.Target> = emptyList())

/** The PC's Control Center: sound, Omarchy's toggles, the power profile (host controls.py). */
data class Controls(
    val volume: Int?, val muted: Boolean, val micMuted: Boolean, val output: String?,
    val nightlight: Boolean, val awake: Boolean, val dnd: Boolean, val recording: Boolean,
    val power: String?, val powers: List<String>, val bluetooth: Boolean,
)

/** A project an agent can start in: its folder on the PC, its name, its herdr workspace if open there. */
data class Project(val path: String, val name: String, val workspace: String?)

/** A subagent an agent launched: what for, which kind, running/done/stale, seconds since it started and since it last wrote. */
data class Subagent(val id: String, val description: String, val type: String, val state: String, val since: Int, val quiet: Int, val said: String)

class NetworkSession(
    private val onError: (String) -> Unit = {},
    private val onWorkspaces: (List<Workspace>) -> Unit = {},
    private val onMonitors: (List<MonitorInfo>) -> Unit = {},
    private val onCursor: (CursorAt?) -> Unit = {},
    private val onAgents: (List<Agent>, Boolean) -> Unit = { _, _ -> },
    private val onAgentAlert: (Agent) -> Unit = {},
    private val onAgentText: (String, String) -> Unit = { _, _ -> },
    /** A page of an agent's conversation (agentHistory), its new messages (agentFollow), an output whole. */
    private val onAgentPage: (AgentPage) -> Unit = {},
    private val onAgentItems: (String, List<ChatItem>, Long) -> Unit = { _, _, _ -> },
    private val onAgentOutput: (String, String, String) -> Unit = { _, _, _ -> },
    /** An agent's project in git ([agentGit]) and one file's diff ([agentGitDiff]). */
    private val onAgentGit: (String, GitSummary) -> Unit = { _, _ -> },
    private val onAgentGitDiff: (String, String, String) -> Unit = { _, _, _ -> },
    /** A monitor's thumbnail (JPEG), as asked with [thumbGet]. */
    private val onThumb: (String, ByteArray) -> Unit = { _, _ -> },
    /** How the PC is doing, as asked with [statsGet]. */
    private val onStats: (PcStats) -> Unit = {},
    /** The PC service's version, as asked with [hostGet]. */
    private val onHost: (HostInfo) -> Unit = {},
    /** A folder of the PC, as asked with [filesList]. */
    private val onFiles: (FolderListing) -> Unit = {},
    /** The PC's toggles, as asked with [controlsGet] (and after each [pcControl]). */
    private val onControls: (Controls) -> Unit = {},
    /** Where an agent can be started ([listProjects]), and the pane of one that started ([startAgent]). */
    private val onProjects: (List<Project>) -> Unit = {},
    private val onAgentStarted: (String) -> Unit = {},
    /** The subagents an agent launched, as asked with [agentSubagents]. */
    private val onAgentSubagents: (String, List<Subagent>) -> Unit = { _, _ -> },
    /** An agent's screen with its colors (ANSI), as asked with [readScreen]. */
    private val onAgentScreen: (String, String) -> Unit = { _, _ -> },
    /** An agent's "/" menu: agent, its kind (claude/codex), the entries. */
    private val onAgentCommands: (String, String, List<SlashCommand>) -> Unit = { _, _, _ -> },
    /** An error the host attached to a negative ack (e.g. herdr refused): shown, not fatal. */
    private val onSoftError: (String) -> Unit = {},
    /** The PC's Omarchy theme: mode (dark/light) and its resolved colors (#rrggbb by key). */
    private val onTheme: (String, Map<String, String>) -> Unit = { _, _ -> },
    private val onClipboard: (PcClip) -> Unit = {},
    /** The PC asks the phone to open "screen", "extra" or "terminal" (Omarchy's Phone menu). */
    private val onOpen: (String) -> Unit = {},
    /** The PC's lock screen as it changes, and the replies about unlocking it. */
    private val onPcLock: (PcLock) -> Unit = {},
    private val onUnlock: (UnlockReply) -> Unit = {},
    /** The PC answered one of the phone's notifications (key, text), or dismissed it. */
    private val onPhoneReply: (String, String) -> Unit = { _, _ -> },
    private val onPhoneDismiss: (String) -> Unit = {},
    /** What plays on the PC, pushed as it changes. */
    private val onMedia: (PcMedia) -> Unit = {},
    /** The PC wants the phone to ring (find it), or to stop. */
    private val onRing: (Boolean) -> Unit = {},
    /** Omarchy's Super+Space menu and apps, as asked with [menuGet]. */
    private val onMenu: (OmarchyMenu) -> Unit = {},
    private val onBinds: (List<Bind>) -> Unit = {},
    private val onNow: (NowState) -> Unit = {},
    private val onWindows: (List<PcWindow>) -> Unit = {},
    /** A file (or print) the PC offers: downloaded over HTTP (FileTransfer). */
    private val onFileOffer: (FileOffer) -> Unit = {},
    /** The PC's AI plans and how much of each is used (usage.get). */
    private val onUsage: (UsageState) -> Unit = {},
    /** A desktop notification shown on the PC. */
    private val onPcNotification: (PcNotification) -> Unit = {},
    private val send: (String) -> Boolean,
) : RemoteInput {
    private val textLock = Mutex()
    private val pendingAcks = mutableMapOf<Int, CompletableDeferred<Boolean>>()
    private var sessionId: String? = null
    private var seq = 0
    private var lastReceivedMs = 0L
    private var closed = false

    val isOpen get() = sessionId != null && !closed

    fun onMessage(text: String, nowMs: Long) {
        val message = JSONObject(text)
        lastReceivedMs = nowMs
        when (message.optString("type")) {
            "session" -> sessionId = message.getString("sessionId")
            "ack" -> {
                pendingAcks.remove(message.getInt("seq"))?.complete(message.optBoolean("ok"))
                if (!message.optBoolean("ok") && message.has("error")) onSoftError(message.getString("error"))
            }
            "agents" -> {
                val list = message.getJSONArray("agents")
                onAgents(List(list.length()) { agentFrom(list.getJSONObject(it)) }, message.optBoolean("available", true))
            }
            "agent.alert" -> onAgentAlert(agentFrom(message.getJSONObject("agent")))
            "agent.text" -> onAgentText(message.getString("id"), message.getString("text"))
            "agent.history" -> onAgentPage(AgentPage(message.getString("id"), ChatItem.list(message.optJSONArray("items")),
                message.optLong("start"), message.optLong("end"), message.optBoolean("more"), message.optBoolean("none")))
            "agent.items" -> onAgentItems(message.getString("id"), ChatItem.list(message.optJSONArray("items")), message.optLong("end"))
            "agent.git" -> onAgentGit(message.getString("id"), GitSummary(
                message.optBoolean("repo"), message.optString("branch"),
                message.optJSONArray("files")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { o -> GitFile(o.optString("path"), o.optString("state"), o.optInt("added"), o.optInt("removed")) } } }.orEmpty(),
                message.optJSONArray("commits")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { o -> GitCommit(o.optString("hash"), o.optString("when"), o.optString("subject")) } } }.orEmpty(),
                message.optInt("ahead"), message.optInt("behind"),
            ))
            "agent.gitdiff" -> onAgentGitDiff(message.getString("id"), message.optString("path"), message.optString("diff"))
            "thumb" -> onThumb(message.getString("monitor"), java.util.Base64.getDecoder().decode(message.getString("jpeg")))
            "stats" -> onStats(PcStats(
                message.optInt("cpu"), message.optDouble("mem_used", 0.0), message.optDouble("mem_total", 0.0),
                if (message.isNull("temp")) null else message.optInt("temp"), if (message.isNull("battery")) null else message.optInt("battery"),
                message.optBoolean("charging"), message.optLong("uptime"),
            ))
            "host" -> onHost(HostInfo(message.optString("version"), message.optBoolean("outdated"), Wake.targets(message.optJSONArray("wake"))))
            "files" -> onFiles(FolderListing(
                message.getString("path"), if (message.isNull("parent")) null else message.optString("parent").ifBlank { null },
                message.optJSONArray("items")?.let { a ->
                    List(a.length()) { i -> a.getJSONObject(i).let { o -> PcFile(o.optString("name"), o.optBoolean("dir"), o.optLong("size"), o.optLong("mtime")) } }
                }.orEmpty(),
                message.optJSONArray("roots")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { o -> o.optString("path") to o.optString("name") } } }.orEmpty(),
            ))
            "controls" -> onControls(Controls(
                if (message.isNull("volume")) null else message.optInt("volume"), message.optBoolean("muted"), message.optBoolean("mic_muted"),
                if (message.isNull("output")) null else message.optString("output").ifBlank { null },
                message.optBoolean("nightlight"), message.optBoolean("awake"), message.optBoolean("dnd"), message.optBoolean("recording"),
                if (message.isNull("power")) null else message.optString("power").ifBlank { null },
                message.optJSONArray("powers")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(), message.optBoolean("bluetooth"),
            ))
            "projects" -> onProjects(message.optJSONArray("items")?.let { a ->
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    Project(o.optString("path"), o.optString("name"), if (o.isNull("workspace")) null else o.optString("workspace").ifBlank { null })
                }
            }.orEmpty())
            "agent.started" -> onAgentStarted(message.getString("id"))
            "agent.subagents" -> onAgentSubagents(message.getString("id"), message.optJSONArray("items")?.let { a ->
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    Subagent(o.optString("id"), o.optString("desc"), o.optString("type"), o.optString("state"), o.optInt("since"), o.optInt("quiet"), o.optString("said"))
                }
            }.orEmpty())
            "agent.screen" -> onAgentScreen(message.getString("id"), message.getString("text"))
            "agent.commands" -> onAgentCommands(message.getString("id"), message.optString("kind"), SlashCommand.list(message.optJSONArray("items")))
            "agent.output" -> onAgentOutput(message.getString("id"), message.getString("call"), message.optString("t"))
            "theme" -> {
                val colors = message.optJSONObject("colors") ?: JSONObject()
                onTheme(message.optString("mode", "dark"), colors.keys().asSequence().associateWith { colors.getString(it) })
            }
            "clipboard" -> onClipboard(PcClip(message.getString("text"), message.optBoolean("auto")))
            "open" -> onOpen(message.getString("what"))
            "phone.ring" -> onRing(message.optBoolean("on", true))
            "pc.lock" -> onPcLock(PcLock(message.optBoolean("locked"), message.optBoolean("enrolled"), message.optBoolean("rule")))
            "unlock.challenge", "unlock.result", "unlock.enrolled" -> onUnlock(UnlockReply(message.getString("type"), message.optBoolean("ok"),
                message.optString("reason").ifEmpty { null }, message.optString("nonce").ifEmpty { null }))
            "phone.reply" -> onPhoneReply(message.getString("key"), message.getString("text"))
            "phone.dismiss" -> onPhoneDismiss(message.getString("key"))
            "media" -> onMedia(PcMedia(message.optBoolean("playing"), message.optString("title"), message.optString("artist"), message.optString("player")))
            "pc.notification" -> onPcNotification(
                PcNotification(message.optString("app"), message.optString("title"), message.optString("body")),
            )
            "file.offer" -> onFileOffer(
                FileOffer(message.getString("id"), message.getString("name"), message.optLong("size", -1),
                    message.optString("kind", "file"), message.optString("tag").ifEmpty { null }),
            )
            "usage" -> onUsage(UsageState.parse(message))
            "now" -> {
                val media = message.optJSONObject("media")
                onNow(NowState(media?.optString("title"), media?.optString("artist"), media?.optBoolean("playing") == true,
                    message.optString("reminder").takeIf { !message.isNull("reminder") && it.isNotEmpty() }, message.optBoolean("update"),
                    media?.optString("player")?.ifBlank { null }))
            }
            "binds" -> {
                val list = message.getJSONArray("binds")
                onBinds(List(list.length()) {
                    val b = list.getJSONObject(it)
                    Bind(b.getString("description"), b.optString("keys"), KeyStroke(b.getInt("usage"), b.optInt("modifiers")))
                })
            }
            "windows" -> {
                val list = message.getJSONArray("windows")
                onWindows(List(list.length()) {
                    val w = list.getJSONObject(it)
                    PcWindow(w.getString("address"), w.optString("title"), w.optString("app"), w.optString("workspace"),
                        w.optBoolean("floating"), w.optBoolean("fullscreen"), w.optBoolean("focused"))
                })
            }
            "menu" -> {
                val list = message.getJSONArray("items")
                val apps = message.optJSONArray("apps") ?: org.json.JSONArray()
                onMenu(OmarchyMenu(
                    List(list.length()) {
                        val m = list.getJSONObject(it)
                        MenuItem(m.getString("id"), m.optString("parent"), m.optString("icon"), m.optString("label"),
                            m.optString("kind", "submenu"), m.optBoolean("checked"), m.optString("description"), m.optString("target"))
                    },
                    List(apps.length()) { apps.getJSONObject(it).let { a -> LauncherApp(a.getString("id"), a.optString("name")) } },
                ))
            }
            "error" -> onError(message.optString("message", tr("Erro no computador.", "Error on the computer.")))
            "monitors" -> {
                val list = message.getJSONArray("monitors")
                onMonitors(
                    List(list.length()) {
                        val m = list.getJSONObject(it)
                        MonitorInfo(m.getString("name"), m.getInt("x"), m.getInt("y"), m.getInt("width"), m.getInt("height"),
                            m.getInt("workspace"), m.getBoolean("extra"))
                    },
                )
            }
            "cursor" -> onCursor(
                if (message.isNull("monitor")) null
                else CursorAt(message.getString("monitor"), message.getDouble("x").toFloat(), message.getDouble("y").toFloat()),
            )
            "workspaces" -> {
                val list = message.getJSONArray("workspaces")
                onWorkspaces(
                    List(list.length()) {
                        val w = list.getJSONObject(it)
                        Workspace(w.getInt("id"), w.getString("monitor"), w.getInt("windows"),
                            w.getBoolean("active"), w.getBoolean("focused"))
                    },
                )
            }
        }
    }

    fun nowGet() {
        message("now.get", JSONObject())
    }

    /** play-pause, next or previous on the PC's player (Omarchy's media keys). */
    suspend fun mediaCmd(action: String): Boolean = acked("media.cmd", JSONObject().put("action", action))

    /** A print at full resolution: the whole desktop, [monitor], or [region] of it (x, y, w, h as fractions).
     * [tag] "save" keeps it in Pictures, "ocr" reads its text. It arrives as a file.offer. */
    suspend fun shotGet(monitor: String?, region: FloatArray?, tag: String): Boolean {
        val payload = JSONObject().put("tag", tag)
        if (monitor != null) payload.put("monitor", monitor)
        if (monitor != null && region != null) {
            payload.put("region", JSONObject().put("x", region[0].toDouble()).put("y", region[1].toDouble())
                .put("w", region[2].toDouble()).put("h", region[3].toDouble()))
        }
        return acked("shot.get", payload, AGENT_ACK_TIMEOUT_MS)
    }

    /** Asks for the AI plans' usage; [force] skips the host's one-minute cache. Arrives as "usage". */
    fun usageGet(force: Boolean = false) {
        message("usage.get", JSONObject().put("force", force))
    }

    /** "lock" or "suspend" on the PC. */
    suspend fun pcAct(action: String): Boolean = acked("pc.act", JSONObject().put("action", action))

    fun bindsGet() {
        message("binds.get", JSONObject())
    }

    fun windowsGet() {
        message("windows.get", JSONObject())
    }

    /** focus, close, float, fullscreen or workspace (with [workspace]) on one of the PC's windows. */
    suspend fun windowAct(address: String, action: String, workspace: Int? = null): Boolean {
        val payload = JSONObject().put("address", address).put("action", action)
        if (workspace != null) payload.put("workspace", workspace)
        return acked("window.act", payload, AGENT_ACK_TIMEOUT_MS)
    }

    fun menuGet() {
        message("menu.get", JSONObject())
    }

    /** Runs a menu item (its own action on the PC) or "app:<desktop id>". */
    suspend fun menuRun(id: String): Boolean = acked("menu.run", JSONObject().put("id", id), AGENT_ACK_TIMEOUT_MS)

    /** Opens the real Omarchy menu on the PC at [route] ("root" for the top). */
    suspend fun menuShow(route: String): Boolean = acked("menu.show", JSONObject().put("route", route), AGENT_ACK_TIMEOUT_MS)

    /** Text for the PC's clipboard (wl-copy); false when too long or not delivered. */
    suspend fun clipboardSet(text: String): Boolean =
        text.length in 1..CLIPBOARD_SET_LIMIT && acked("clipboard.set", JSONObject().put("text", text))

    /** Asks for the PC's clipboard; it arrives as a "clipboard" message. */
    fun clipboardGet() {
        message("clipboard.get", JSONObject())
    }

    /** Whether the phone shows the cursor map; the PC only reads the cursor while it does. */
    fun watch(cursor: Boolean) {
        message("watch", JSONObject().put("cursor", cursor))
    }

    /** One of the phone's notifications, for the PC to show (the person opted in). */
    fun phoneNotification(key: String, app: String, title: String, text: String, reply: Boolean) {
        message("phone.notification", JSONObject().put("key", key.take(300)).put("app", app.take(60))
            .put("title", MirrorRules.clip(title, 200)).put("text", MirrorRules.clip(text, 1500)).put("reply", reply))
    }

    fun unlockEnroll(publicKey: String) = message("unlock.enroll", JSONObject().put("key", publicKey))
    fun unlockChallenge() = message("unlock.challenge", JSONObject())
    fun unlockRespond(signature: String) = message("unlock.respond", JSONObject().put("signature", signature))

    fun phoneNotificationRemoved(key: String) {
        message("phone.notification.removed", JSONObject().put("key", key.take(300)))
    }

    fun phoneStatus(battery: Int, charging: Boolean) {
        message("phone.status", JSONObject().put("battery", battery.coerceIn(0, 100)).put("charging", charging))
    }

    fun readAgent(id: String, lines: Int = 40) {
        message("agent.read", JSONObject().put("id", id).put("lines", lines))
    }

    /** A page of the agent's conversation ending at byte [before] (the end: null). */
    fun agentHistory(id: String, before: Long?, limit: Int) {
        message("agent.history", JSONObject().put("id", id).apply { if (before != null) put("before", before) }.put("limit", limit))
    }

    /** New messages of this agent, pushed from byte [after] on, until [agentUnfollow]. */
    fun agentFollow(id: String, after: Long) {
        message("agent.follow", JSONObject().put("id", id).put("after", after))
    }

    fun agentUnfollow() {
        message("agent.unfollow", JSONObject())
    }

    fun agentGit(id: String) {
        message("agent.git", JSONObject().put("id", id))
    }

    fun agentGitDiff(id: String, path: String) {
        message("agent.gitdiff", JSONObject().put("id", id).put("path", path))
    }

    /** A small JPEG of one of the PC's monitors, [width] pixels wide. */
    fun thumbGet(monitor: String, width: Int) {
        message("thumb.get", JSONObject().put("monitor", monitor).put("width", width))
    }

    fun statsGet() {
        message("stats.get", JSONObject())
    }

    fun hostGet() {
        message("host.get", JSONObject())
    }

    /** The PC service restarts itself (new code): the connection drops and comes back. */
    suspend fun hostRestart(): Boolean = acked("host.restart", JSONObject())

    /** A folder of the PC (its Downloads when [path] is null). */
    fun filesList(path: String?) {
        message("files.list", JSONObject().apply { if (path != null) put("path", path) })
    }

    /** Brings a PC file to the phone: the PC offers it (file.offer) and the phone downloads it. */
    suspend fun filesFetch(path: String): Boolean = acked("files.fetch", JSONObject().put("path", path))

    /** A web link opened in the PC's browser. */
    suspend fun openUrl(url: String): Boolean = acked("pc.open_url", JSONObject().put("url", url))

    fun controlsGet() {
        message("controls.get", JSONObject())
    }

    /** One of the PC's controls: a toggle, or the volume (0–100) or power profile given as [value]. */
    suspend fun pcControl(id: String, value: Any? = null): Boolean =
        acked("pc.control", JSONObject().put("id", id).apply { if (value != null) put("value", value) })

    fun listProjects() {
        message("projects.list", JSONObject())
    }

    /** A new agent of [kind] in the project at [cwd], with [prompt] as its task: true once it is ready. */
    suspend fun startAgent(cwd: String, kind: String, prompt: String): Boolean =
        acked("agent.start", JSONObject().put("cwd", cwd).put("kind", kind).put("prompt", prompt), START_TIMEOUT_MS)

    /** The subagents this agent launched (running, done, stopped). */
    fun agentSubagents(id: String) {
        message("agent.subagents", JSONObject().put("id", id))
    }

    /** The agent's screen as the terminal shows it, colors included. */
    fun readScreen(id: String) {
        message("agent.read", JSONObject().put("id", id).put("lines", 60).put("ansi", true))
    }

    /** The agent's "/" menu (its CLI's commands and what is installed on the PC). */
    fun agentCommands(id: String) {
        message("agent.commands", JSONObject().put("id", id))
    }

    /** An action's whole output, from where the page said it is. */
    fun agentOutput(id: String, at: Long, call: String) {
        message("agent.output", JSONObject().put("id", id).put("at", at).put("call", call))
    }

    suspend fun agentKeys(id: String, keys: List<String>): Boolean = acked("agent.keys", JSONObject().put("id", id).put("keys", JSONArray(keys)), AGENT_ACK_TIMEOUT_MS)

    suspend fun agentPrompt(id: String, text: String): Boolean = acked("agent.prompt", JSONObject().put("id", id).put("text", text), AGENT_ACK_TIMEOUT_MS)

    suspend fun focusAgent(id: String): Boolean = acked("agent.focus", JSONObject().put("id", id), AGENT_ACK_TIMEOUT_MS)

    private fun agentFrom(a: JSONObject) = Agent(
        a.getString("id"), a.optString("kind"), a.optString("status", "unknown"), a.optString("title"),
        a.optString("workspace"), a.optString("cwd"), a.optInt("seq"), a.optInt("subagents"),
    )

    fun goToWorkspace(id: Int) {
        message("workspace.go", JSONObject().put("id", id))
    }

    /** +1 next, -1 previous workspace with windows (like Omarchy's Super+Tab). */
    fun stepWorkspace(direction: Int) {
        message("workspace.step", JSONObject().put("direction", direction))
    }

    fun ping() {
        message("ping", JSONObject())
    }

    fun isExpired(nowMs: Long) = nowMs - lastReceivedMs > HEARTBEAT_TIMEOUT_MS

    override fun movePointer(dx: Int, dy: Int) {
        message("pointer.move", JSONObject().put("dx", dx).put("dy", dy))
    }

    override fun scroll(steps: Int) {
        message("pointer.scroll", JSONObject().put("vertical", steps))
    }

    override fun setMouseButtons(buttons: Int) {
        message("pointer.buttons", JSONObject().put("mask", buttons))
    }

    /** A single key waits for text in progress, so it never lands in the middle of it. */
    override suspend fun tapKey(key: KeyStroke): Boolean = textLock.withLock { tap(key) }

    override suspend fun type(keys: List<KeyStroke>): Int = textLock.withLock {
        var sent = 0
        for (key in keys) {
            if (!tap(key)) break
            sent++
        }
        sent
    }

    /** Delivered means the host processed the key (ack), not that the focused app accepted it. */
    private suspend fun tap(key: KeyStroke): Boolean =
        acked("keyboard.tap", JSONObject().put("usage", key.usage).put("modifiers", key.modifiers))

    /** Sends and waits for the host's ack (true = processed). */
    private suspend fun acked(type: String, payload: JSONObject, timeoutMs: Long = ACK_TIMEOUT_MS): Boolean {
        val seq = message(type, payload) ?: return false
        val ack = CompletableDeferred<Boolean>()
        pendingAcks[seq] = ack
        return withTimeoutOrNull(timeoutMs) { ack.await() } ?: false.also { pendingAcks.remove(seq) }
    }

    override fun releaseAll() {
        message("input.releaseAll", JSONObject())
    }

    fun close() {
        if (closed) return
        releaseAll()
        closed = true
        pendingAcks.values.forEach { it.complete(false) }
        pendingAcks.clear()
    }

    /** Returns the sequence number, or null when nothing could be sent. */
    private fun message(type: String, payload: JSONObject): Int? {
        val id = sessionId ?: return null
        if (closed) return null
        val next = seq + 1
        val envelope = JSONObject().put("v", 1).put("sessionId", id).put("seq", next).put("type", type).put("payload", payload)
        val text = envelope.toString()
        // The host reads at most MAX_FRAME bytes; accents and emoji take more bytes than characters.
        if (text.toByteArray(Charsets.UTF_8).size > MAX_FRAME) return null
        if (!send(text)) return null
        seq = next
        return next
    }

    companion object {
        const val ACK_TIMEOUT_MS = 2_000L
        /** host/keypad_host/protocol.py MAX_FRAME, in UTF-8 bytes. */
        const val MAX_FRAME = 16 * 1024
        /** host/keypad_host/protocol.py CLIPBOARD_SET_LIMIT: keeps the message under the host's frame limit. */
        const val CLIPBOARD_SET_LIMIT = 12_000
        /** herdr calls take up to 5 s on the host (host/keypad_host/herdr.py); wait longer than that. */
        const val AGENT_ACK_TIMEOUT_MS = 8_000L
        /** Starting an agent: herdr waits for it to be ready (up to a minute). */
        const val START_TIMEOUT_MS = 80_000L
        const val PING_INTERVAL_MS = 2_000L
        const val HEARTBEAT_TIMEOUT_MS = 6_000L
    }
}

/** A file the PC offers (file.offer): kind "file" or "shot"; tag "ocr" when the phone asked to read its text. */
data class FileOffer(val id: String, val name: String, val size: Long, val kind: String, val tag: String?)

/** A notification the PC showed (app, title, text), for the phone's "Avisos do PC" channel. */
/** The PC's clipboard text: asked for (or sent from its menu), or auto when copied there with sync on. */
data class PcClip(val text: String, val auto: Boolean)

data class PcNotification(val app: String, val title: String, val body: String)
