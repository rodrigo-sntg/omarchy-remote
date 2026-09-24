package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.SlashCommand
import com.sandevsystems.omarchyremote.network.slashMatches
import com.sandevsystems.omarchyremote.network.slashQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlashMenuTest {
    private val menu = listOf(
        SlashCommand("/model", "Set the AI model", "Escolher o modelo", "", true),
        SlashCommand("/compact", "Free up context", "Liberar contexto", "", false),
        SlashCommand("/superpowers:brainstorming", "Use before creative work", null, "superpowers", false),
        SlashCommand("/output-style", "List output styles", null, "", true),
        SlashCommand("\$imagegen", "Generate images", null, "skill", false),
    )

    @Test
    fun theMenuOpensOnlyWhileTheFirstWordIsBeingTyped() {
        assertEquals("/", slashQuery("/"))
        assertEquals("/mo", slashQuery("/mo"))
        assertEquals("\$ima", slashQuery("\$ima"))
        assertNull(slashQuery("/model "))
        assertNull(slashQuery("olá /mo"))
        assertNull(slashQuery(""))
    }

    @Test
    fun namesThatStartWithItComeFirstThenInsideThenDescriptions() {
        assertEquals(listOf("/model", "/compact", "/superpowers:brainstorming", "/output-style"), slashMatches(menu, "/").map { it.name })
        assertEquals(listOf("/output-style"), slashMatches(menu, "/out").map { it.name })
        assertEquals(listOf("/superpowers:brainstorming"), slashMatches(menu, "/brain").map { it.name })
        assertEquals(listOf("/compact"), slashMatches(menu, "/context").map { it.name })  // by its description
        assertEquals(listOf("\$imagegen"), slashMatches(menu, "\$").map { it.name })
    }

    @Test
    fun theSessionAsksAndReadsTheMenu() {
        val got = mutableListOf<Triple<String, String, List<SlashCommand>>>()
        val s = NetworkSession(onAgentCommands = { id, kind, items -> got += Triple(id, kind, items) }) { true }
        s.onMessage("""{"type":"agent.commands","id":"w1:p1","kind":"claude","items":[{"n":"/model","d":"Set the AI model","g":"","pt":"Escolher o modelo","live":true},{"n":"/omarchy","d":"Desktop","g":"skill"}]}""", 0)
        assertEquals(Triple("w1:p1", "claude", listOf(SlashCommand("/model", "Set the AI model", "Escolher o modelo", "", true), SlashCommand("/omarchy", "Desktop", null, "skill", false))), got.single())
    }
}
