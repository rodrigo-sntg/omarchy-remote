package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.AgentConversation
import com.sandevsystems.omarchyremote.network.AgentConversation.Did
import com.sandevsystems.omarchyremote.ui.longLabel
import com.sandevsystems.omarchyremote.ui.shortLabel
import com.sandevsystems.omarchyremote.ui.describe
import com.sandevsystems.omarchyremote.ui.doing
import org.junit.Assert.assertEquals
import org.junit.Test

/** Claude Code's actions in the person's words (unit tests run in Portuguese). */
class AgentWordsTest {
    @Test
    fun claudeCodesOwnSummariesAreTranslated() {
        assertEquals("Leu 1 arquivo, rodou 3 comandos", describe(Did("", "Read 1 file, ran 3 shell commands")))
        assertEquals("Buscou 2 padrões", describe(Did("", "Searched for 2 patterns")))
        assertEquals("Rodando ./gradlew test…", describe(Did("", "Running ./gradlew test…")))
        assertEquals("Something new", describe(Did("", "Something new")))
    }

    @Test
    fun whatARunningAgentIsDoingIsSaidShort() {
        assertEquals("Rodando um comando", doing(Did("", "Running SP=/tmp/x; mkdir -p \$SP…")))
        assertEquals("Rodando um comando", doing(Did("Bash", "./gradlew assembleDebug")))
        assertEquals("Editando App.kt", doing(Did("Update", "app/src/App.kt")))
        assertEquals("Lendo herdr.py", doing(Did("Read", "host/keypad_host/herdr.py")))
    }

    @Test
    fun runningAndBackgroundCommandsToo() {
        assertEquals("Rodando 1 comando…", describe(Did("", "Running 1 shell command…")))
        assertEquals("Comando em segundo plano terminou: SP=/tmp/x", describe(Did("", "Background command \"SP=/tmp/x\" completed (exit code 0)")))
        assertEquals("Comando em segundo plano falhou: make", describe(Did("", "Background command \"make\" failed with exit code 2")))
    }

    @Test
    fun anEditRequestOffersAcceptEditsNotASecondYes() {
        val chat = AgentConversation.parse("""
● Update(README.md)

──────────────────────────────────────────────────────────
 Edit file
 README.md
 Do you want to make this edit to README.md?
 ❯ 1. Yes
   2. Yes, and switch to accept edits (auto-approve file edits and common file commands) for this session (shift+tab)
   3. No
""".trimIndent())!!
        val options = chat.ask!!.options
        assertEquals(AgentConversation.Choice.ALWAYS, options[1].choice)
        assertEquals(listOf("Sim", "Sim, e aceitar edições nesta sessão", "Não, dizer o que fazer"), options.map { longLabel(it) })
        assertEquals("Aceitar edições", shortLabel(options[1]))
    }

    @Test
    fun anUnknownYesKeepsTheAgentsOwnWords() {
        val o = AgentConversation.Option("2", "Yes, allow reading from src/ during this session", AgentConversation.Choice.ALWAYS)
        assertEquals("Yes, allow reading from src/ during this session", longLabel(o))
    }
}
