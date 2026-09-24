package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.AgentConversation
import com.sandevsystems.omarchyremote.network.AgentNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What an agent's notification says and which answers it offers (unit tests run in Portuguese). */
class AgentNoticeTest {
    private fun agent(status: String) = Agent("w1:p1", "claude", status, "Pareamento com o S24", "w1", "omarchy-remote", 1)

    @Test
    fun aPermissionRequestShowsTheCommandAndItsAnswers() {
        val chat = AgentConversation.parse("""
● Vou rodar o build.

──────────────────────────────────────────────────────────
 Bash command

   ./gradlew assembleDebug
   Build the debug APK

 Do you want to proceed?
 ❯ 1. Yes
   2. Yes, and don't ask again for ./gradlew commands
   3. No, and tell Claude what to do differently (esc)
""".trimIndent())
        val n = AgentNotice.of(agent("blocked"), chat)
        assertEquals("Claude precisa de você", n.title)
        assertEquals("Pareamento com o S24 · quer rodar: ./gradlew assembleDebug", n.body)
        assertEquals("1", n.allow)
        assertEquals("3", n.deny)
        assertEquals("3", n.replyAfter)  // "No, and tell Claude what to do": refuse, then the reply
    }

    @Test
    fun aFinishedAgentShowsTheStartOfItsReply() {
        val chat = AgentConversation.parse("● Pronto: o pareamento funciona e os testes passam.\n\n✻ Worked for 2m 10s")
        val n = AgentNotice.of(agent("done"), chat)
        assertEquals("Claude terminou", n.title)
        assertEquals("Pronto: o pareamento funciona e os testes passam.", n.body)
        assertNull(n.allow)
        assertNull(n.replyAfter)
    }

    @Test
    fun withoutTheScreenItSaysWhoAndWhere() {
        val n = AgentNotice.of(agent("blocked"), null)
        assertEquals("Pareamento com o S24 · omarchy-remote", n.body)
        assertNull(n.allow)
        assertNull(n.deny)
    }
}
