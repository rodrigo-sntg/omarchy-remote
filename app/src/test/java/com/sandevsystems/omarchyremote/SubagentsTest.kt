package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.Subagent
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentsTest {
    @Test
    fun theListSaysHowManySubagentsRunAndTheDetailWhichOnes() {
        val agents = mutableListOf<List<Agent>>()
        val subs = mutableListOf<Pair<String, List<Subagent>>>()
        val s = NetworkSession(onAgents = { list, _ -> agents += list }, onAgentSubagents = { id, items -> subs += id to items }) { true }
        s.onMessage("""{"type":"agents","available":true,"agents":[{"id":"w1:p1","kind":"claude","status":"idle","title":"T","workspace":"w1","cwd":"app","seq":1,"subagents":2},{"id":"w2:p1","kind":"codex","status":"idle","title":"C","workspace":"w2","cwd":"x","seq":1}]}""", 0)
        assertEquals(listOf(2, 0), agents.single().map { it.subagents })
        s.onMessage("""{"type":"agent.subagents","id":"w1:p1","items":[{"id":"a1","desc":"i18n sweep","type":"general-purpose","state":"running","since":65,"quiet":5,"said":""}]}""", 0)
        assertEquals("w1:p1" to listOf(Subagent("a1", "i18n sweep", "general-purpose", "running", 65, 5, "")), subs.single())
    }
}
