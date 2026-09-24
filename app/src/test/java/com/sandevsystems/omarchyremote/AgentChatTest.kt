package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.AgentPage
import com.sandevsystems.omarchyremote.network.ChatBlock
import com.sandevsystems.omarchyremote.network.ChatItem
import com.sandevsystems.omarchyremote.network.ChatState
import com.sandevsystems.omarchyremote.network.NetworkSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentChatTest {
    private fun page(items: List<ChatItem>, start: Long, end: Long, more: Boolean, id: String = "w1:p1") = AgentPage(id, items, start, end, more, none = false)

    @Test
    fun theFirstPageThenOlderOnTopThenNewAtTheBottom() {
        var chat = ChatState("w1:p1")
        chat = chat.page(page(listOf(ChatItem.You("c"), ChatItem.Said("d")), 200, 300, more = true))
        assertEquals(listOf("c", "d"), chat.entries.map { (it.item as? ChatItem.You)?.text ?: (it.item as ChatItem.Said).text })
        chat = chat.page(page(listOf(ChatItem.You("a"), ChatItem.Said("b")), 0, 200, more = false))
        chat = chat.newItems("w1:p1", listOf(ChatItem.Said("e")), 350)
        assertEquals(listOf("a", "b", "c", "d", "e"), chat.entries.map { (it.item as? ChatItem.You)?.text ?: (it.item as ChatItem.Said).text })
        assertEquals(0L, chat.start)
        assertEquals(350L, chat.end)
        assertFalse(chat.more)
        // Keys stay: what was on screen keeps its key while pages come above and below.
        assertEquals(chat.entries.map { it.seq }, chat.entries.map { it.seq }.sorted())
        assertEquals(chat.entries.size, chat.entries.map { it.seq }.toSet().size)
    }

    @Test
    fun anotherAgentsOrAStalePageIsIgnored() {
        val chat = ChatState("w1:p1").page(page(listOf(ChatItem.Said("x")), 100, 200, more = true))
        assertEquals(chat, chat.page(page(listOf(ChatItem.Said("y")), 0, 100, more = false, id = "w2:p1")))
        assertEquals(chat, chat.page(page(listOf(ChatItem.Said("y")), 0, 50, more = false)))
        assertEquals(chat, chat.newItems("w2:p1", listOf(ChatItem.Said("z")), 300))
    }

    @Test
    fun noSessionFileMeansTheScreen() {
        val chat = ChatState("w1:p1").page(AgentPage("w1:p1", emptyList(), 0, 0, false, none = true))
        assertTrue(chat.none)
        assertFalse(chat.loading)
    }

    @Test
    fun actionsInARowAreOneBlockWithTheirOutputs() {
        val chat = ChatState("w1:p1").page(page(listOf(
            ChatItem.Said("vou rodar"),
            ChatItem.Tool("t1", "Bash", "ls"), ChatItem.Out("t1", "a b", cut = false, err = false, at = null),
            ChatItem.Tool("t2", "Read", "x.kt"), ChatItem.Out("t2", "...", cut = true, err = false, at = 90),
            ChatItem.Said("pronto"),
        ), 0, 100, more = false))
        val blocks = chat.blocks()
        assertEquals(3, blocks.size)
        val actions = blocks[1] as ChatBlock.Actions
        assertEquals(listOf("t1", "t2"), actions.steps.map { it.tool?.id })
        assertEquals("a b", actions.steps[0].out?.text)
        assertEquals(90L, actions.steps[1].out?.at)
    }

    @Test
    fun theSessionReadsTheNewMessages() {
        val pages = mutableListOf<AgentPage>()
        val news = mutableListOf<Triple<String, List<ChatItem>, Long>>()
        val outputs = mutableListOf<Triple<String, String, String>>()
        val s = NetworkSession(onAgentPage = { pages += it }, onAgentItems = { id, items, end -> news += Triple(id, items, end) },
            onAgentOutput = { id, call, text -> outputs += Triple(id, call, text) }) { true }
        s.onMessage("""{"type":"agent.history","id":"w1:p1","items":[{"k":"you","t":"oi"},{"k":"tool","id":"t1","n":"Bash","t":"ls"},{"k":"out","id":"t1","t":"x","cut":true,"err":false,"at":5000000000}],"start":10,"end":99,"more":true}""", 0)
        s.onMessage("""{"type":"agent.history","id":"w1:p2","none":true}""", 0)
        s.onMessage("""{"type":"agent.items","id":"w1:p1","items":[{"k":"said","t":"ok"}],"end":120}""", 0)
        s.onMessage("""{"type":"agent.output","id":"w1:p1","call":"t1","t":"tudo"}""", 0)
        assertEquals(AgentPage("w1:p1", listOf(ChatItem.You("oi"), ChatItem.Tool("t1", "Bash", "ls"), ChatItem.Out("t1", "x", true, false, 5_000_000_000)), 10, 99, true, false), pages[0])
        assertTrue(pages[1].none)
        assertEquals(Triple("w1:p1", listOf<ChatItem>(ChatItem.Said("ok")), 120L), news.single())
        assertEquals(Triple("w1:p1", "t1", "tudo"), outputs.single())
    }

    @Test
    fun theSessionAsksForPagesFollowsAndFetchesOutputs() {
        val sent = mutableListOf<JSONObject>()
        val s = NetworkSession { sent += JSONObject(it); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.agentHistory("w1:p1", null, 40)
        s.agentHistory("w1:p1", 1234, 40)
        s.agentFollow("w1:p1", 99)
        s.agentUnfollow()
        s.agentOutput("w1:p1", 5000, "t1")
        fun JSONObject.fields() = keys().asSequence().sorted().joinToString(" ") { "$it=${get(it)}" }
        val bodies = sent.filter { it.has("payload") }.map { it.getString("type") to it.getJSONObject("payload").fields() }
        assertEquals(listOf(
            "agent.history" to "id=w1:p1 limit=40",
            "agent.history" to "before=1234 id=w1:p1 limit=40",
            "agent.follow" to "after=99 id=w1:p1",
            "agent.unfollow" to "",
            "agent.output" to "at=5000 call=t1 id=w1:p1",
        ), bodies)
    }

    @Test
    fun anEditComesWithWhatChanged() {
        val items = ChatItem.list(org.json.JSONArray("""[{"k":"tool","id":"t","n":"Edit","t":"/a/App.kt","old":"val x = 1","new":"val x = 2"},
            {"k":"tool","id":"u","n":"Bash","t":"ls","why":"List files"},{"k":"tool","id":"v","n":"apply_patch","t":"","patch":"-a\n+b"}]"""))
        assert(items[0] == ChatItem.Tool("t", "Edit", "/a/App.kt", old = "val x = 1", new = "val x = 2"))
        assert(items[1] == ChatItem.Tool("u", "Bash", "ls", why = "List files"))
        assert(items[2] == ChatItem.Tool("v", "apply_patch", "", patch = "-a\n+b"))
    }
}
