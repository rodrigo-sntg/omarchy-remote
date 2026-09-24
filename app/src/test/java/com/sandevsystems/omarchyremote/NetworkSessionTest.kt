package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.PcLock
import com.sandevsystems.omarchyremote.network.UnlockReply
import com.sandevsystems.omarchyremote.network.PcClip
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSessionTest {
    private val sent = mutableListOf<JSONObject>()
    private val session = NetworkSession { text -> sent += JSONObject(text); true }

    private fun started() = session.also { it.onMessage("""{"type":"session","sessionId":"abc"}""", 0) }
    private fun ack(seq: Int, ok: Boolean = true) = session.onMessage("""{"type":"ack","seq":$seq,"ok":$ok}""", 0)

    @Test
    fun nothingIsSentBeforeTheHostOpensASession() = runTest {
        session.movePointer(5, 5)
        assertFalse(session.tapKey(KeyStroke(0x04)))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun envelopesCarrySessionAndIncreasingSequence() {
        started().movePointer(12, -3)
        session.scroll(-1)
        session.setMouseButtons(1)
        assertEquals(listOf(1, 2, 3), sent.map { it.getInt("seq") })
        assertTrue(sent.all { it.getInt("v") == 1 && it.getString("sessionId") == "abc" })
        assertEquals("pointer.move", sent[0].getString("type"))
        assertEquals(12, sent[0].getJSONObject("payload").getInt("dx"))
        assertEquals(-3, sent[0].getJSONObject("payload").getInt("dy"))
        assertEquals(-1, sent[1].getJSONObject("payload").getInt("vertical"))
        assertEquals(1, sent[2].getJSONObject("payload").getInt("mask"))
    }

    @Test
    fun keyTapIsDeliveredOnlyWhenTheHostAcks() = runTest {
        started()
        val tap = async { session.tapKey(KeyStroke(0x04, 0x01)) }
        runCurrent()
        val message = sent.single()
        assertEquals("keyboard.tap", message.getString("type"))
        assertEquals(4, message.getJSONObject("payload").getInt("usage"))
        assertEquals(1, message.getJSONObject("payload").getInt("modifiers"))
        ack(message.getInt("seq"))
        assertTrue(tap.await())
    }

    @Test
    fun missingOrNegativeAckMeansNotDelivered() = runTest {
        started()
        val timedOut = async { session.tapKey(KeyStroke(0x04)) }
        advanceTimeBy(NetworkSession.ACK_TIMEOUT_MS + 1)
        assertFalse(timedOut.await())

        val refused = async { session.tapKey(KeyStroke(0x05)) }
        runCurrent()
        ack(sent.last().getInt("seq"), ok = false)
        assertFalse(refused.await())
    }

    @Test
    fun textStopsAtTheFirstUndeliveredKey() = runTest {
        started()
        val typing = async { session.type(listOf(KeyStroke(0x04), KeyStroke(0x05), KeyStroke(0x06))) }
        runCurrent()
        ack(sent.last().getInt("seq"))
        runCurrent()
        ack(sent.last().getInt("seq"), ok = false)
        assertEquals(1, typing.await())
        assertEquals(2, sent.count { it.getString("type") == "keyboard.tap" })
    }

    @Test
    fun closeReleasesOnceAndThenSendsNothing() = runTest {
        started()
        val pending = async { session.tapKey(KeyStroke(0x04)) }
        runCurrent()
        session.close()
        assertFalse(pending.await())
        assertEquals("input.releaseAll", sent.last().getString("type"))
        val count = sent.size
        session.movePointer(1, 1)
        assertFalse(session.tapKey(KeyStroke(0x04)))
        assertEquals(count, sent.size)
    }

    @Test
    fun heartbeatExpiresWithoutTrafficFromHost() {
        started()
        session.ping()
        assertEquals("ping", sent.last().getString("type"))
        assertFalse(session.isExpired(NetworkSession.HEARTBEAT_TIMEOUT_MS))
        session.onMessage("""{"type":"pong","seq":1}""", 5_000)
        assertFalse(session.isExpired(10_000))
        assertTrue(session.isExpired(5_000 + NetworkSession.HEARTBEAT_TIMEOUT_MS + 1))
    }

    @Test
    fun hostErrorIsReported() {
        var error: String? = null
        val session = NetworkSession(onError = { error = it }) { true }
        session.onMessage("""{"type":"error","message":"Já existe outro controlador conectado."}""", 0)
        assertEquals("Já existe outro controlador conectado.", error)
    }

    @Test
    fun workspaceListFromTheHostIsReported() {
        var received: List<com.sandevsystems.omarchyremote.network.Workspace>? = null
        val session = NetworkSession(onWorkspaces = { received = it }) { true }
        session.onMessage(
            """{"type":"workspaces","workspaces":[{"id":1,"monitor":"DP-1","windows":4,"active":false,"focused":false},""" +
                """{"id":2,"monitor":"DP-1","windows":2,"active":true,"focused":true}]}""",
            0,
        )
        assertEquals(listOf(1, 2), received!!.map { it.id })
        assertTrue(received!![1].active && received!![1].focused)
        assertEquals(4, received!![0].windows)
    }

    @Test
    fun workspaceCommandsAreSent() {
        started().goToWorkspace(3)
        session.stepWorkspace(-1)
        assertEquals("workspace.go", sent[0].getString("type"))
        assertEquals(3, sent[0].getJSONObject("payload").getInt("id"))
        assertEquals("workspace.step", sent[1].getString("type"))
        assertEquals(-1, sent[1].getJSONObject("payload").getInt("direction"))
    }

    @Test
    fun monitorLayoutAndCursorFromTheHostAreReported() {
        var layout: List<com.sandevsystems.omarchyremote.network.MonitorInfo>? = null
        var cursor: com.sandevsystems.omarchyremote.network.CursorAt? = com.sandevsystems.omarchyremote.network.CursorAt("x", 0f, 0f)
        val session = NetworkSession(onMonitors = { layout = it }, onCursor = { cursor = it }) { true }
        session.onMessage(
            """{"type":"monitors","monitors":[{"name":"DP-1","x":0,"y":0,"width":1920,"height":1080,"workspace":2,"extra":false},""" +
                """{"name":"OMARCHYREMOTE","x":1920,"y":0,"width":1560,"height":720,"workspace":3,"extra":true}]}""",
            0,
        )
        assertEquals(listOf("DP-1", "OMARCHYREMOTE"), layout!!.map { it.name })
        assertEquals(1920, layout!![1].x)
        assertTrue(layout!![1].extra)
        assertEquals(2, layout!![0].workspace)
        session.onMessage("""{"type":"cursor","monitor":"DP-1","x":0.25,"y":0.5}""", 0)
        assertEquals(com.sandevsystems.omarchyremote.network.CursorAt("DP-1", 0.25f, 0.5f), cursor)
        session.onMessage("""{"type":"cursor","monitor":null}""", 0)
        assertEquals(null, cursor)
    }

    private val agentsSeen = mutableListOf<Pair<List<Agent>, Boolean>>()
    private val alerts = mutableListOf<Agent>()
    private val texts = mutableListOf<Pair<String, String>>()
    private val softErrors = mutableListOf<String>()
    private val agentSession = NetworkSession(
        onAgents = { list, available -> agentsSeen += list to available },
        onAgentAlert = { alerts += it },
        onAgentText = { id, text -> texts += id to text },
        onSoftError = { softErrors += it },
    ) { text -> sent += JSONObject(text); true }

    private val agentJson = """{"id":"w1:p1","kind":"claude","status":"blocked","title":"Fix tests","workspace":"w1","cwd":"proj","seq":7}"""

    @Test
    fun agentsAndAlertsAreParsed() {
        agentSession.onMessage("""{"type":"agents","agents":[$agentJson],"available":true}""", 0)
        agentSession.onMessage("""{"type":"agents","agents":[],"available":false}""", 0)
        agentSession.onMessage("""{"type":"agent.alert","agent":$agentJson}""", 0)
        agentSession.onMessage("""{"type":"agent.text","id":"w1:p1","text":"last\nlines"}""", 0)
        assertEquals(Agent("w1:p1", "claude", "blocked", "Fix tests", "w1", "proj", 7), agentsSeen[0].first.single())
        assertTrue(agentsSeen[0].second)
        assertTrue(agentsSeen[1].first.isEmpty() && !agentsSeen[1].second)
        assertEquals("w1:p1", alerts.single().id)
        assertEquals("w1:p1" to "last\nlines", texts.single())
    }

    @Test
    fun agentCommandsAreEnvelopedAndAcked() = runTest {
        agentSession.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        agentSession.readAgent("w1:p1", 20)
        assertEquals("agent.read", sent.last().getString("type"))
        assertEquals(20, sent.last().getJSONObject("payload").getInt("lines"))
        val keys = async { agentSession.agentKeys("w1:p1", listOf("enter", "y")) }
        runCurrent()
        val message = sent.last()
        assertEquals("agent.keys", message.getString("type"))
        assertEquals("enter", message.getJSONObject("payload").getJSONArray("keys").getString(0))
        agentSession.onMessage("""{"type":"ack","seq":${message.getInt("seq")},"ok":false,"error":"agent target w1:p1 not found"}""", 0)
        assertFalse(keys.await())
        assertEquals("agent target w1:p1 not found", softErrors.single())
        val prompt = async { agentSession.agentPrompt("w1:p1", "go on") }
        runCurrent()
        assertEquals("go on", sent.last().getJSONObject("payload").getString("text"))
        agentSession.onMessage("""{"type":"ack","seq":${sent.last().getInt("seq")},"ok":true}""", 0)
        assertTrue(prompt.await())
    }

    @Test
    fun agentCommandsWaitLongerThanHerdrMayTake() = runTest {
        agentSession.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        val keys = async { agentSession.agentKeys("w1:p1", listOf("enter")) }
        runCurrent()
        advanceTimeBy(NetworkSession.ACK_TIMEOUT_MS + 1_000) // past a key tap's wait, herdr still working
        agentSession.onMessage("""{"type":"ack","seq":${sent.last().getInt("seq")},"ok":true}""", 0)
        assertTrue(keys.await())
    }

    @Test
    fun themeClipboardAndOpenArePassedOn() {
        val themes = mutableListOf<Pair<String, Map<String, String>>>()
        val clips = mutableListOf<PcClip>()
        val opens = mutableListOf<String>()
        val s = NetworkSession(
            onTheme = { mode, colors -> themes += mode to colors },
            onClipboard = { clips += it },
            onOpen = { opens += it },
        ) { true }
        s.onMessage("""{"type":"theme","name":"solitude","mode":"dark","colors":{"accent":"#798186","background":"#101315"}}""", 0)
        s.onMessage("""{"type":"clipboard","text":"olá"}""", 0)
        s.onMessage("""{"type":"clipboard","text":"copiado no PC","auto":true}""", 0)
        s.onMessage("""{"type":"open","what":"terminal"}""", 0)
        assertEquals("dark" to mapOf("accent" to "#798186", "background" to "#101315"), themes.single())
        assertEquals(listOf(PcClip("olá", auto = false), PcClip("copiado no PC", auto = true)), clips)
        assertEquals("terminal", opens.single())
    }

    @Test
    fun thePcCanMakeThePhoneRingAndStop() {
        val rings = mutableListOf<Boolean>()
        val s = NetworkSession(onRing = { rings += it }) { true }
        s.onMessage("""{"type":"phone.ring","on":true}""", 0)
        s.onMessage("""{"type":"phone.ring","on":false}""", 0)
        assertEquals(listOf(true, false), rings)
    }

    @Test
    fun phoneNotificationsGoToThePcAndItsAnswersComeBack() {
        started().phoneNotification("0|com.whatsapp|1", "WhatsApp", "Ana", "chego às 8", reply = true)
        val m = sent.last()
        assertEquals("phone.notification", m.getString("type"))
        val p = m.getJSONObject("payload")
        assertEquals("0|com.whatsapp|1", p.getString("key"))
        assertEquals("WhatsApp", p.getString("app"))
        assertEquals(true, p.getBoolean("reply"))
        session.phoneNotificationRemoved("0|com.whatsapp|1")
        assertEquals("phone.notification.removed", sent.last().getString("type"))

        val replies = mutableListOf<Pair<String, String>>()
        val dismissed = mutableListOf<String>()
        val s = NetworkSession(onPhoneReply = { k, t -> replies += k to t }, onPhoneDismiss = { dismissed += it }) { true }
        s.onMessage("""{"type":"phone.reply","key":"k1","text":"combinado"}""", 0)
        s.onMessage("""{"type":"phone.dismiss","key":"k2"}""", 0)
        assertEquals(listOf("k1" to "combinado"), replies)
        assertEquals(listOf("k2"), dismissed)
    }

    @Test
    fun unlockingThePcGoesBackAndForth() {
        started().unlockEnroll("MFkwEw==")
        assertEquals("unlock.enroll", sent.last().getString("type"))
        assertEquals("MFkwEw==", sent.last().getJSONObject("payload").getString("key"))
        session.unlockChallenge()
        assertEquals("unlock.challenge", sent.last().getString("type"))
        session.unlockRespond("MEUCIQ==")
        assertEquals("MEUCIQ==", sent.last().getJSONObject("payload").getString("signature"))

        val locks = mutableListOf<PcLock>()
        val replies = mutableListOf<UnlockReply>()
        val s = NetworkSession(onPcLock = { locks += it }, onUnlock = { replies += it }) { true }
        s.onMessage("""{"type":"pc.lock","locked":true,"enrolled":true,"rule":false}""", 0)
        s.onMessage("""{"type":"unlock.challenge","ok":true,"nonce":"abcd"}""", 0)
        s.onMessage("""{"type":"unlock.result","ok":false,"reason":"bad-signature"}""", 0)
        s.onMessage("""{"type":"unlock.enrolled","ok":true,"enrolled":true,"rule":true}""", 0)
        assertEquals(PcLock(locked = true, enrolled = true, rule = false), locks.single())
        assertEquals(listOf(
            UnlockReply("unlock.challenge", true, null, "abcd"),
            UnlockReply("unlock.result", false, "bad-signature", null),
            UnlockReply("unlock.enrolled", true, null, null),
        ), replies)
    }

    @Test
    fun phoneStatusIsEnveloped() {
        started().phoneStatus(57, charging = true)
        val m = sent.last()
        assertEquals("phone.status", m.getString("type"))
        assertEquals(57, m.getJSONObject("payload").getInt("battery"))
        assertTrue(m.getJSONObject("payload").getBoolean("charging"))
    }

    @Test
    fun clipboardGoesToThePcAndIsAskedBack() = runTest {
        started()
        val set = async { session.clipboardSet("do celular") }
        runCurrent()
        assertEquals("clipboard.set", sent.last().getString("type"))
        assertEquals("do celular", sent.last().getJSONObject("payload").getString("text"))
        ack(sent.last().getInt("seq"))
        assertTrue(set.await())
        session.clipboardGet()
        assertEquals("clipboard.get", sent.last().getString("type"))
        assertFalse(session.clipboardSet("x".repeat(NetworkSession.CLIPBOARD_SET_LIMIT + 1)))
    }

    @Test
    fun pcActionsAndVolumeAreSentAndAcked() = runTest {
        started()
        val lock = async { session.pcAct("lock") }
        runCurrent()
        assertEquals("pc.act", sent.last().getString("type"))
        assertEquals("lock", sent.last().getJSONObject("payload").getString("action"))
        ack(sent.last().getInt("seq"))
        assertTrue(lock.await())
        val volume = async { session.mediaCmd("volume-up") }
        runCurrent()
        assertEquals("volume-up", sent.last().getJSONObject("payload").getString("action"))
        ack(sent.last().getInt("seq"))
        assertTrue(volume.await())
    }

    @Test
    fun fileOffersAndPrintRequests() = runTest {
        var offer: com.sandevsystems.omarchyremote.network.FileOffer? = null
        val s = NetworkSession(onFileOffer = { offer = it }) { text -> sent += JSONObject(text); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.onMessage("""{"type":"file.offer","id":"k3y","name":"relatório.pdf","size":4500,"kind":"file"}""", 0)
        assertEquals(com.sandevsystems.omarchyremote.network.FileOffer("k3y", "relatório.pdf", 4500, "file", null), offer)
        s.onMessage("""{"type":"file.offer","id":"z9","name":"print.png","size":10,"kind":"shot","tag":"ocr"}""", 0)
        assertEquals("ocr", offer?.tag)
        val shot = async { s.shotGet("DP-1", floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), "ocr") }
        runCurrent()
        val payload = sent.last().getJSONObject("payload")
        assertEquals("shot.get", sent.last().getString("type"))
        assertEquals("DP-1", payload.getString("monitor"))
        assertEquals(0.3, payload.getJSONObject("region").getDouble("w"), 1e-6)
        assertEquals("ocr", payload.getString("tag"))
        s.onMessage("""{"type":"ack","seq":${sent.last().getInt("seq")},"ok":true}""", 0)
        assertTrue(shot.await())
    }

    @Test
    fun pcNotificationsArePassedOn() {
        var got: com.sandevsystems.omarchyremote.network.PcNotification? = null
        val s = NetworkSession(onPcNotification = { got = it }) { true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.onMessage("""{"type":"pc.notification","app":"Slack","title":"Ana","body":"Reunião em 5 min"}""", 0)
        assertEquals(com.sandevsystems.omarchyremote.network.PcNotification("Slack", "Ana", "Reunião em 5 min"), got)
    }

    @Test
    fun aMessageOverTheHostFrameLimitIsNotSent() = runTest {
        started()
        val before = sent.size
        // Under the character limit, but 4 bytes each in UTF-8: over the host's 16 KB frame.
        assertFalse(session.clipboardSet("😀".repeat(5_000)))
        assertEquals(before, sent.size)
        // Nothing was used up: the next message still goes out with the next sequence number.
        session.clipboardGet()
        assertEquals(1, sent.last().getInt("seq"))
    }

    @Test
    fun omarchyMenuIsParsedAndItemsRunOnThePc() = runTest {
        var menu: com.sandevsystems.omarchyremote.network.OmarchyMenu? = null
        val s = NetworkSession(onMenu = { menu = it }) { text -> sent += JSONObject(text); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.menuGet()
        assertEquals("menu.get", sent.last().getString("type"))
        s.onMessage("""{"type":"menu","items":[{"id":"system","parent":"","icon":"S","label":"System","kind":"submenu","checked":false},
            {"id":"system.lock","parent":"system","icon":"L","label":"Lock","kind":"action","checked":true,"description":"now"}],
            "apps":[{"id":"chromium","name":"Chromium"}]}""", 0)
        assertEquals(listOf("system.lock"), menu!!.children("system").map { it.id })
        assertTrue(menu!!.byId("system.lock")!!.checked)
        assertEquals("Chromium", menu!!.apps.single().name)
        val run = async { s.menuRun("app:chromium") }
        runCurrent()
        assertEquals("menu.run", sent.last().getString("type"))
        assertEquals("app:chromium", sent.last().getJSONObject("payload").getString("id"))
        s.onMessage("""{"type":"ack","seq":${sent.last().getInt("seq")},"ok":true}""", 0)
        assertTrue(run.await())
        s.menuShow("system")
        assertEquals("system", sent.last().getJSONObject("payload").getString("route"))
    }

    @Test
    fun bindsAndWindowsAreParsedAndActedOn() = runTest {
        var binds: List<com.sandevsystems.omarchyremote.network.Bind> = emptyList()
        var windows: List<com.sandevsystems.omarchyremote.network.PcWindow> = emptyList()
        val s = NetworkSession(onBinds = { binds = it }, onWindows = { windows = it }) { text -> sent += JSONObject(text); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.bindsGet(); assertEquals("binds.get", sent.last().getString("type"))
        s.windowsGet(); assertEquals("windows.get", sent.last().getString("type"))
        s.onMessage("""{"type":"binds","binds":[{"description":"Close window","keys":"Super + W","usage":26,"modifiers":8}]}""", 0)
        s.onMessage("""{"type":"windows","windows":[{"address":"0x1a","title":"notes","app":"Alacritty","workspace":"2","floating":false,"fullscreen":false,"focused":true}]}""", 0)
        assertEquals(com.sandevsystems.omarchyremote.input.KeyStroke(26, 8), binds.single().stroke)
        assertTrue(windows.single().focused)
        val act = async { s.windowAct("0x1a", "workspace", 3) }
        runCurrent()
        assertEquals(3, sent.last().getJSONObject("payload").getInt("workspace"))
        s.onMessage("""{"type":"ack","seq":${sent.last().getInt("seq")},"ok":true}""", 0)
        assertTrue(act.await())
    }

    @Test
    fun nowAndMediaControl() = runTest {
        var now: com.sandevsystems.omarchyremote.network.NowState? = null
        val s = NetworkSession(onNow = { now = it }) { text -> sent += JSONObject(text); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.nowGet(); assertEquals("now.get", sent.last().getString("type"))
        s.onMessage("""{"type":"now","media":{"playing":true,"artist":"Radiohead","title":"Reckoner"},"reminder":null,"update":true}""", 0)
        assertEquals("Reckoner", now!!.mediaTitle)
        assertTrue(now!!.playing && now!!.update)
        assertEquals(null, now!!.reminder)
        val cmd = async { s.mediaCmd("next") }
        runCurrent()
        assertEquals("next", sent.last().getJSONObject("payload").getString("action"))
        s.onMessage("""{"type":"ack","seq":${sent.last().getInt("seq")},"ok":true}""", 0)
        assertTrue(cmd.await())
    }
}
