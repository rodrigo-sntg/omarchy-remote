package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.network.SeenAgents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeenAgentsTest {
    private fun agent(status: String, seq: Int = 5, id: String = "w1:p1") = Agent(id, "claude", status, "t", "w1", "proj", seq)

    @Before
    fun clear() = SeenAgents.clear()

    @Test
    fun aPermissionRequestAlwaysNeedsYou() {
        SeenAgents.saw(agent("blocked"))
        assertTrue(AgentsText.needsYou(agent("blocked")))
    }

    @Test
    fun aFinishedAgentNeedsYouOnlyUntilOpenedHere() {
        assertTrue(AgentsText.needsYou(agent("done")))
        SeenAgents.saw(agent("done"))
        assertFalse(AgentsText.needsYou(agent("done")))
        // It worked and finished again: that one is new.
        assertTrue(AgentsText.needsYou(agent("done", seq = 9)))
    }

    @Test
    fun seeingOneAgentLeavesTheOthers() {
        SeenAgents.saw(agent("done"))
        assertTrue(AgentsText.needsYou(agent("done", id = "w2:p1")))
    }

    @Test
    fun idleOrWorkingNeverNeedYou() {
        assertFalse(AgentsText.needsYou(agent("idle")))
        assertFalse(AgentsText.needsYou(agent("working")))
    }

    @Test
    fun theCountsLeaveOutWhatWasSeen() {
        val done = agent("done", id = "w2:p1")
        SeenAgents.saw(done)
        assertEquals("Ligado a pc · 2 agentes · 1 esperando você", AgentsText.status("pc", listOf(agent("blocked"), done)))
        assertEquals(listOf("Precisam de você", "Parados"), AgentsText.groups(listOf(agent("blocked"), done)).map { it.first })
    }
}

class SeenAgentsSavedTest {
    @Test
    fun whatWasSeenSurvivesARestart() {
        SeenAgents.clear()
        var saved = ""
        SeenAgents.onChange = { saved = it }
        SeenAgents.saw(Agent("w1:p1", "claude", "done", "t", "w1", "p", 7))
        SeenAgents.clear()
        SeenAgents.load(saved)
        assertTrue(SeenAgents.seen(Agent("w1:p1", "claude", "done", "t", "w1", "p", 7)))
        SeenAgents.load("lixo;=;a=b")
        SeenAgents.onChange = {}
    }
}

class AgentOrderTest {
    private fun agent(id: String, active: Long, seq: Int = 1) = Agent(id, "claude", "idle", id, "w1", "p", seq, active = active)

    @Test
    fun theMostRecentlyActiveComeFirst() {
        val list = listOf(agent("a", 100), agent("b", 300), agent("c", 200))
        assertEquals(listOf("b", "c", "a"), AgentsText.byActivity(list).map { it.id })
    }

    @Test
    fun withoutATimeTheyGoLastInHerdrsOrder() {
        val list = listOf(agent("x", 0, seq = 2), agent("a", 100), agent("y", 0, seq = 9))
        assertEquals(listOf("a", "y", "x"), AgentsText.byActivity(list).map { it.id })
    }
}
