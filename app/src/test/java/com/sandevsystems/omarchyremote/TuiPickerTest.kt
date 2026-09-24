package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.TuiPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuiPickerTest {
    private fun screen(name: String) = javaClass.getResource("/screens/$name")!!.readText()

    @Test
    fun claudesModelPickerIsAList() {
        val p = TuiPicker.parse(screen("claude-model.txt"))!!
        assertEquals("Select model", p.title)
        assertEquals(listOf("Default (recommended)", "Opus (1M context)", "Fable", "Sonnet", "Haiku"), p.options.map { it.label })
        assertEquals("Opus 5.5 with 1M context · Best for everyday, complex tasks", p.options[0].detail)
        assertEquals("Fable 5.1 · Most capable for your hardest and longest-running tasks", p.options[2].detail)
        assertEquals(listOf(false, true, false, false, false), p.options.map { it.current })
        assertEquals("5", p.options[4].key)
        assertEquals(1, p.cursor)  // "❯" is on Opus
        assertEquals(listOf("down", "down", "enter"), p.keysTo(3))
        assertEquals(listOf("up", "enter"), p.keysTo(0))
    }

    @Test
    fun codexsModelPickerToo() {
        val p = TuiPicker.parse(screen("codex-model.txt"))!!
        assertEquals("Select Model and Effort", p.title)
        assertEquals("gpt-6-astra", p.options[0].label)
        assertEquals(true, p.options[0].current)
        assertEquals("Workhorse model for coding and everyday work.", p.options[1].detail)
        assertEquals(8, p.options.size)
        assertEquals(0, p.cursor)
    }

    @Test
    fun aPermissionQuestionIsNotAPicker() {
        assertNull(TuiPicker.parse("""
────────────────────────────
 Bash command

   ls

 Do you want to proceed?
 ❯ 1. Yes
   2. No
""".trimIndent()))
    }

    @Test
    fun theModelInUseIsReadFromTheStatusLine() {
        assertEquals("Opus 5.5 (1M context)", TuiPicker.model("  ~/dev/app on main | Opus 5.5 (1M context) ctx 70%\n  ⏵⏵ auto mode on", "claude"))
        assertEquals("Sonnet 5", TuiPicker.model("  ~/dev/app | Sonnet 5\n", "claude"))
        assertEquals("gpt-6-astra high", TuiPicker.model("› Ask Codex\n  gpt-6-astra high fast · ~/dev/app · Title", "codex"))
        assertNull(TuiPicker.model("nothing here", "claude"))
    }
}
