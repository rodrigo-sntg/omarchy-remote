package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.AgentScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentScreenTest {
    private val claude = """
⏺ Pronto: a lupa segue o cursor e os botões não ficam mais espremidos.

  Commit e8b3f34.

* Worked for 9m 30s done 11:35 PM 1 shell still running

╭──────────────────────────────────────────────╮
│ >                                            │
╰──────────────────────────────────────────────╯
  ? for shortcuts                ⏵⏵ accept edits on (shift+tab to cycle)
Update available! Run: mise upgrade claude

~/dev/projects/omarchy-remote on main | Opus 5.5 (1M context) ctx 42%
""".trimIndent()

    @Test
    fun theAgentsMessageStaysAndTheScreenChromeGoes() {
        val screen = AgentScreen.clean(claude)
        assertEquals("⏺ Pronto: a lupa segue o cursor e os botões não ficam mais espremidos.\n\n  Commit e8b3f34.", screen.body)
    }

    @Test
    fun whatMattersInTheChromeBecomesAShortFooter() {
        assertEquals("Trabalhou 9m 30s · 1 shell rodando · contexto 42%", AgentScreen.clean(claude).footer)
    }

    @Test
    fun whileWorkingTheFooterSaysSo() {
        val working = "⏺ Lendo arquivos…\n\n✻ Pondering… (12s · ↑ 1.2k tokens · esc to interrupt)\n\n│ > │\n"
        val screen = AgentScreen.clean(working)
        assertEquals("⏺ Lendo arquivos…", screen.body)
        assertEquals("Trabalhando… 12s", screen.footer)
    }

    @Test
    fun anotherProgramsTextIsLeftAlone() {
        val shell = "$ ls\nREADME.md  app  host\n$ "
        val screen = AgentScreen.clean(shell)
        assertEquals("$ ls\nREADME.md  app  host\n$", screen.body)
        assertNull(screen.footer)
    }

    @Test
    fun theInputBoxCutsTheScreenEvenWithTeamLinesBelowIt() {
        // Claude Code with background agents: under the input box and status line come team lines.
        val team = listOf(
            "⏺ Revisei as telas e ajustei os ícones.",
            "",
            "✻ Brewing… (7s · ↓ 707 tokens)",
            "────────────────────────────────────",
            "❯ ",
            "────────────────────────────────────",
            "  ~/dev/projects/omarchy-remote on main | Opus 5.5 ctx 42%",
            "  ⏵⏵ bypass permissions on · 1 shell",
            "",
            "● main",
            "○ general-purpose  Read… 54s · ↓ 130.8k tokens",
            "□ canvas",
        ).joinToString("\n")
        val screen = AgentScreen.clean(team)
        assertEquals("⏺ Revisei as telas e ajustei os ícones.", screen.body)
    }

    @Test
    fun codexsInputBoxAndItsSparklesGoToo() {
        val codex = """
• Ran ./gradlew test
  └ BUILD SUCCESSFUL in 20s

• Tudo verde.

                              ⠁    ⠈
⠂                                   ⠁
›⠁Ask Codex to do anything    ⠈
⠄                        ⠁  ⠂       ⠁

  gpt-6-astra high fast · ~/dev/projects/omarchy-remote · Criar controle
""".trimIndent()
        assertEquals("• Ran ./gradlew test\n  └ BUILD SUCCESSFUL in 20s\n\n• Tudo verde.", AgentScreen.clean(codex).body)
    }
}
