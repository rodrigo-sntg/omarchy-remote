package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.QuickAction
import com.sandevsystems.omarchyremote.network.contextUsed
import com.sandevsystems.omarchyremote.network.quickActions
import com.sandevsystems.omarchyremote.network.separatorBefore
import com.sandevsystems.omarchyremote.network.timeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Quick actions and the chat's times (unit tests run in Portuguese). */
class QuickActionsTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 9, day, h, m, 0, 0, zone).toEpochSecond()

    @Test
    fun timesReadLikeAChat() {
        val now = at(24, 15, 0)
        assertEquals("Hoje 10:02", timeLabel(at(24, 10, 2), now, zone))
        assertEquals("Ontem 18:40", timeLabel(at(23, 18, 40), now, zone))
        assertEquals("20/09 09:05", timeLabel(at(20, 9, 5), now, zone))
    }

    @Test
    fun aSeparatorAfterABreakOrANewDay() {
        assertTrue(separatorBefore(null, at(24, 10, 0), zone))
        assertFalse(separatorBefore(at(24, 10, 0), at(24, 10, 15), zone))
        assertTrue(separatorBefore(at(24, 10, 0), at(24, 10, 25), zone))
        assertTrue(separatorBefore(at(23, 23, 55), at(24, 0, 5), zone))
    }

    @Test
    fun theContextInUseIsReadForBothAgents() {
        assertEquals(87, contextUsed("  ~/dev/app on main | Opus 5.5 (1M context) ctx 87%"))
        assertEquals(72, contextUsed("  gpt-6-astra high · 28% context left · ~/dev/app"))
        assertEquals(null, contextUsed("nothing"))
    }

    @Test
    fun actionsFitTheAgentAndTheMoment() {
        assertEquals(listOf(QuickAction("stop", "Interromper", keys = listOf("esc"))), quickActions("claude", "working", null, 0))
        assertEquals(emptyList<QuickAction>(), quickActions("claude", "blocked", 90, 3))
        val idle = quickActions("claude", "idle", 87, 3)
        assertEquals(listOf("compact", "continue", "review", "cross_review", "recap"), idle.map { it.id })
        assertEquals("Compactar 87%", idle[0].label)
        assertTrue(idle[0].urgent)
        assertEquals("/code-review", idle[2].prompt)
        assertEquals("/review", quickActions("codex", "done", 20, 1).first { it.id == "review" }.prompt)
        assertEquals(listOf("continue", "recap"), quickActions("codex", "done", 20, 0).map { it.id })
    }
}
