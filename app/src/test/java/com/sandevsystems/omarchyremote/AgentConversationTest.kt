package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.AgentConversation
import com.sandevsystems.omarchyremote.network.AgentConversation.Did
import com.sandevsystems.omarchyremote.network.AgentConversation.Said
import com.sandevsystems.omarchyremote.network.AgentConversation.You
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConversationTest {
    private val finished = """
  ainda do pedido anterior, cortado no topo.

❯ Reconectei o celular. Olha o log
  de novo?

● Bash(grep agent.read ~/.config/herdr/herdr-server.log | tail -3)
  ⎿  2026-09-24T12:56:03Z INFO api request completed
     2026-09-24T12:56:06Z INFO api request completed

● Read(host/keypad_host/herdr.py)
  ⎿  Read 90 lines

● Reconectado (92% de bateria). O log do herdr não mostra
  falha desde 12:56.

  Abra o preview e olhe o terminal do PC.

✻ Worked for 32s · 2 shells still running

──────────────────────────────────────────────────────────
❯
──────────────────────────────────────────────────────────
  ~/dev/projects/omarchy-remote on main | ctx 9%
  ⏵⏵ auto mode on (shift+tab to cycle)
""".trimIndent()

    @Test
    fun claudeCodesScreenReadsAsAConversation() {
        val chat = AgentConversation.parse(finished)!!
        assertEquals(
            listOf(
                Said("ainda do pedido anterior, cortado no topo."),
                You("Reconectei o celular. Olha o log de novo?"),
                Did("Bash", "grep agent.read ~/.config/herdr/herdr-server.log | tail -3"),
                Did("Read", "host/keypad_host/herdr.py"),
                Said("Reconectado (92% de bateria). O log do herdr não mostra falha desde 12:56.\n\nAbra o preview e olhe o terminal do PC."),
            ),
            chat.items,
        )
        assertNull(chat.ask)
    }

    @Test
    fun listsAndTablesKeepTheirLines() {
        val chat = AgentConversation.parse("""
● O que mudou:
  - a lupa segue o cursor
  - os botões respiram
  ┌──────┬─────┐
  │ Tela │ ok  │
  └──────┴─────┘
""".trimIndent())!!
        assertEquals(Said("O que mudou:\n- a lupa segue o cursor\n- os botões respiram\n┌──────┬─────┐\n│ Tela │ ok  │\n└──────┴─────┘"), chat.items.single())
    }

    @Test
    fun anotherAgentsScreenIsNotAConversation() {
        assertNull(AgentConversation.parse("• Ran cargo test\n  └ ok\n\n• Tudo verde.\n\n› Write tests"))
        assertNull(AgentConversation.parse("just a shell\n$ ls\nfile.txt"))
    }

    @Test
    fun aPermissionRequestIsReadWithItsOptions() {
        val chat = AgentConversation.parse("""
● Pronto, o pareamento está no BluetoothHid.kt. Vou rodar o build.

● Bash(./gradlew assembleDebug)
  ⎿  Running…

──────────────────────────────────────────────────────────
 Bash command

   ./gradlew assembleDebug
   Build the debug APK

 Do you want to proceed?
 ❯ 1. Yes
   2. Yes, and don't ask again for ./gradlew commands in /home/u/app
   3. No, and tell Claude what to do differently (esc)
""".trimIndent())!!
        val ask = chat.ask!!
        assertEquals("Bash command", ask.title)
        assertEquals("./gradlew assembleDebug\nBuild the debug APK", ask.detail)
        assertEquals("Do you want to proceed?", ask.question)
        assertEquals(listOf("1", "2", "3"), ask.options.map { it.key })
        assertEquals(AgentConversation.Choice.YES, ask.options[0].choice)
        assertEquals(AgentConversation.Choice.ALWAYS, ask.options[1].choice)
        assertEquals(AgentConversation.Choice.NO, ask.options[2].choice)
        assertEquals("No, and tell Claude what to do differently", ask.options[2].label)
        // The request is not also a message of the conversation.
        assertTrue(chat.items.none { it is Said && "Do you want" in it.text })
    }

    @Test
    fun theOldBoxedPermissionRequestToo() {
        val ask = AgentConversation.parse("""
● Vou editar.

╭──────────────────────────────────────────╮
│ Edit file                                │
│ ╭──────────────────────────────────────╮ │
│ │ src/App.kt                           │ │
│ ╰──────────────────────────────────────╯ │
│ Do you want to make this edit to App.kt? │
│ ❯ 1. Yes                                 │
│   2. No, and tell Claude what to do (esc)│
╰──────────────────────────────────────────╯
""".trimIndent())!!.ask!!
        assertEquals("Edit file", ask.title)
        assertEquals("src/App.kt", ask.detail)
        assertEquals("Do you want to make this edit to App.kt?", ask.question)
        assertEquals(listOf(AgentConversation.Choice.YES, AgentConversation.Choice.NO), ask.options.map { it.choice })
    }

    @Test
    fun aNumberedListInAReplyIsNoRequest() {
        val chat = AgentConversation.parse("""
╭──────────────────────────╮
│ ✻ Welcome to Claude Code │
╰──────────────────────────╯

● Qual você prefere?
  1. Manter o anel
  2. Trocar por barras

──────────────────────────────────────────────────────────
❯ 
──────────────────────────────────────────────────────────
""".trimIndent())!!
        assertNull(chat.ask)
        assertEquals(Said("Qual você prefere?\n1. Manter o anel\n2. Trocar por barras"), chat.items.single())
    }

    @Test
    fun longCallsAndGroupedActionsAreActions() {
        val chat = AgentConversation.parse("""
● Bash(python3 - <<'EOF'
      print("oi")
      EOF)
  ⎿  oi

● Read 2 files (ctrl+o to expand)
  ⎿  app/App.kt

● Running ./gradlew test…
  ⎿  $ ./gradlew test

● Pronto.
""".trimIndent())!!
        assertEquals(
            listOf(Did("Bash", "python3 - <<'EOF'"), Did("", "Read 2 files"), Did("", "Running ./gradlew test…"), Said("Pronto.")),
            chat.items,
        )
    }

    @Test
    fun claudeCodesCollapsedSummaryIsAnAction() {
        val chat = AgentConversation.parse("""
● Vou olhar.

● Read 1 file, ran 3 shell commands (ctrl+o to expand)

● Searched for 2 patterns

● Pronto.
""".trimIndent())!!
        assertEquals(
            listOf(Said("Vou olhar."), Did("", "Read 1 file, ran 3 shell commands"), Did("", "Searched for 2 patterns"), Said("Pronto.")),
            chat.items,
        )
    }

    @Test
    fun summariesWithoutTheMarkAreActionsToo() {
        val chat = AgentConversation.parse("""
● Vou conferir.

  Ran 3 shell commands

● Background command "SP=/tmp/x; cd host" completed (exit code 0)

  Read 1 file

● Pronto.

  Running 1 shell command…
""".trimIndent())!!
        assertEquals(
            listOf(Said("Vou conferir."), Did("", "Ran 3 shell commands"), Did("", "Background command \"SP=/tmp/x; cd host\" completed (exit code 0)"),
                Did("", "Read 1 file"), Said("Pronto."), Did("", "Running 1 shell command…")),
            chat.items,
        )
    }

    @Test
    fun anEditsDashedRulesAreNotShownAsText() {
        val chat = AgentConversation.parse("""
● Update(README.md)

──────────────────────────────────────────────────────────
 Edit file
 README.md
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
  5      python pomodoro.py --rounds 4
  7 +Set custom work and break lengths
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
 Do you want to make this edit to README.md?
 ❯ 1. Yes
   2. No
""".trimIndent())!!
        val ask = chat.ask!!
        assertEquals("Edit file", ask.title)
        assertTrue(ask.detail, ask.detail.lines().none { it.isNotBlank() && it.all { c -> c in "╌-─ " } })
        assertTrue(ask.detail.contains("+Set custom work and break lengths"))
    }
}
