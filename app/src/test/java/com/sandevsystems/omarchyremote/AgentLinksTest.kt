package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.AgentLinks
import com.sandevsystems.omarchyremote.network.RelayHeader
import com.sandevsystems.omarchyremote.network.quickActions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLinksTest {
    private val message = JSONObject()
        .put("links", JSONArray()
            .put(JSONObject().put("id", "l1").put("kind", "review").put("from", "w9:p2").put("to", "w9:p4").put("state", "working").put("since", 100))
            .put(JSONObject().put("id", "l2").put("kind", "relay").put("from", "w9:p2").put("to", "w9:p1").put("state", "done").put("since", 90))
            .put(JSONObject().put("id", "l3").put("kind", "ask").put("from", JSONObject.NULL).put("to", "w9:p1").put("state", "working").put("since", 95)))
        .put("notices", JSONArray()
            .put(JSONObject().put("id", "n1").put("kind", "review").put("origin", "w9:p2").put("from", "w9:p4").put("fromKind", "codex")
                .put("text", "Dois riscos.").put("ts", 200)))

    @Test
    fun readsWhatThePcSends() {
        val links = AgentLinks.parse(message)
        assertEquals(3, links.links.size)
        assertNull(links.links[2].from)
        assertEquals("Dois riscos.", links.notices.single().text)
    }

    @Test
    fun whoIsTalkingWithWhomRightNow() {
        val links = AgentLinks.parse(message)
        assertEquals(listOf("w9:p4"), links.talkingWith("w9:p2"))          // the relay is done: only the review
        assertEquals(listOf("w9:p2"), links.talkingWith("w9:p4"))
        assertEquals(emptyList<String>(), links.talkingWith("w9:p1"))       // asked from a plain terminal
        assertEquals("w9:p4", links.reviewing("w9:p2")?.to)
        assertNull(links.reviewing("w9:p4"))
        assertEquals(listOf("n1"), links.noticesFor("w9:p2").map { it.id })
    }

    @Test
    fun aMessageFromAnotherAgentIsKnownByItsFirstLine() {
        val relayed = RelayHeader.parse("↪ Claude · omarchy-remote · w9:p2\n\nO que acha?\n\n> Use a fila.")!!
        assertEquals("claude", relayed.kind)
        assertEquals("omarchy-remote", relayed.project)
        assertEquals("w9:p2", relayed.pane)
        assertEquals("relay", relayed.what)
        assertEquals("O que acha?\n\n> Use a fila.", relayed.body)
        assertEquals("review", RelayHeader.parse("↪ Codex · my app · w1:p10 · review\n\nRevise…")!!.what)
        assertNull(RelayHeader.parse("uma mensagem qualquer"))
        // The note for the agent at the end of a question isn't for the person.
        val ask = RelayHeader.parse("↪ Claude · app · w9:p2 · ask\n\nResponda só: OK\n\n(Answer directly and briefly: your reply goes back to the agent that asked.)")!!
        assertEquals("Responda só: OK", ask.body)
    }

    @Test
    fun theOtherAgentReviewsFromAChip() {
        val claude = quickActions("claude", "idle", null, changed = 3)
        assertEquals("codex", claude.single { it.id == "cross_review" }.reviewer)
        assertEquals("claude", quickActions("codex", "done", null, changed = 1).single { it.id == "cross_review" }.reviewer)
        assertTrue(quickActions("claude", "idle", null, changed = 0).none { it.id == "cross_review" })
        assertTrue(quickActions("claude", "working", null, changed = 5).none { it.id == "cross_review" })
    }
}
