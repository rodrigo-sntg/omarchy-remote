package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.AgentsText
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentsTextTest {
    private fun agent(status: String, title: String = "Fix tests", kind: String = "claude") = Agent("w1:p1", kind, status, title, "w1", "proj", 1)

    @Test
    fun statusLineCountsAgentsAndTheOnesWaiting() {
        assertEquals("Ligado a meu-pc", AgentsText.status("meu-pc", emptyList()))
        assertEquals("Ligado a meu-pc · 1 agente", AgentsText.status("meu-pc", listOf(agent("working"))))
        assertEquals("Ligado a meu-pc · 3 agentes · 2 esperando você", AgentsText.status("meu-pc", listOf(agent("blocked"), agent("done"), agent("idle"))))
    }

    @Test
    fun alertsSayWhatHappenedInPlainWords() {
        assertEquals("Claude precisa de você", AgentsText.alertTitle(agent("blocked")))
        assertEquals("Codex terminou", AgentsText.alertTitle(agent("done", kind = "codex")))
        assertEquals("Fix tests · proj", AgentsText.alertBody(agent("blocked")))
        assertEquals("proj", AgentsText.alertBody(agent("blocked", title = "")))
    }

    @Test
    fun theListPutsWhoNeedsYouFirst() {
        val groups = AgentsText.groups(listOf(agent("idle"), agent("working"), agent("done"), agent("blocked"), agent("unknown")))
        assertEquals(listOf("Precisam de você", "Trabalhando", "Parados"), groups.map { it.first })
        assertEquals(listOf("blocked", "done"), groups[0].second.map { it.status })
        assertEquals(listOf("idle", "unknown"), groups[2].second.map { it.status })
        assertEquals(emptyList<String>(), AgentsText.groups(listOf(agent("idle"))).drop(1).map { it.first })
    }

    @Test
    fun theHomeScreenWidgetSaysWhatMattersFirst() {
        assertEquals("Desconectado" to "Omarchy Remote", AgentsText.widget(connected = false, agents = emptyList()))
        assertEquals("Sem agentes" to "herdr", AgentsText.widget(connected = true, agents = emptyList()))
        assertEquals("2 esperando você" to "3 agentes no herdr", AgentsText.widget(true, listOf(agent("blocked"), agent("done"), agent("working"))))
        assertEquals("1 trabalhando" to "1 agente no herdr", AgentsText.widget(true, listOf(agent("working"))))
    }
}
