package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.UsageMetric
import com.sandevsystems.omarchyremote.network.UsageProvider
import com.sandevsystems.omarchyremote.network.UsageState
import com.sandevsystems.omarchyremote.network.UsageText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class UsageTextTest {
    private val now = Instant.parse("2026-09-24T03:00:00Z")

    @Test
    fun theResetIsSaidAsHowLongUntilIt() {
        assertEquals("volta em 1h 30", UsageText.reset(Instant.parse("2026-09-24T04:30:00Z"), now))
        assertEquals("volta em 13h", UsageText.reset(Instant.parse("2026-09-24T16:00:00Z"), now))
        assertEquals("volta em 1d 9h", UsageText.reset(Instant.parse("2026-09-25T12:40:00Z"), now))
        assertEquals("volta em 12 min", UsageText.reset(Instant.parse("2026-09-24T03:12:00Z"), now))
        assertEquals("voltando agora", UsageText.reset(Instant.parse("2026-09-24T02:59:00Z"), now))
    }

    @Test
    fun paceComparesUseWithTheTimeThatPassed() {
        assertEquals("7 pts acima do ritmo", UsageText.pace(77, 70))
        assertEquals("21 pts abaixo do ritmo", UsageText.pace(59, 80))
        assertEquals("no ritmo", UsageText.pace(71, 70))
        assertNull(UsageText.pace(50, null))
    }

    @Test
    fun windowsHaveFriendlyNames() {
        assertEquals("5 horas", UsageText.label(UsageMetric("session", "Session", 10, null, null)))
        assertEquals("Semana", UsageText.label(UsageMetric("week", "Weekly", 10, null, null)))
        assertEquals("Fable · semana", UsageText.label(UsageMetric("model", "Fable", 10, null, null)))
    }

    @Test
    fun theHostMessageIsParsedAndProvidersWithoutNumbersAreLeftOut() {
        val state = UsageState.parse(JSONObject("""{"type":"usage","available":true,"providers":[
            {"id":"anthropic","name":"Claude","plan":"Max 5x","stale":false,"error":null,"resets":0,"metrics":[
              {"kind":"session","label":"Session","percent":76,"resetAt":"2026-09-24T04:30:00Z","elapsed":70}]},
            {"id":"zai","name":"Z.AI","plan":"","stale":false,"error":null,"resets":0,"metrics":[]}]}"""))
        assertEquals(1, state.providers.size)
        val claude = state.providers.single()
        assertEquals(UsageProvider("anthropic", "Claude", "Max 5x", false, null, 0,
            listOf(UsageMetric("session", "Session", 76, Instant.parse("2026-09-24T04:30:00Z"), 70))), claude)
        assertEquals(1, state.hidden)
    }

    @Test
    fun theTightestLimitSumsItUp() {
        val state = UsageState(true, listOf(
            UsageProvider("a", "Claude", "", false, null, 0, listOf(UsageMetric("session", "", 40, null, 50), UsageMetric("week", "", 91, null, 92))),
            UsageProvider("o", "Codex", "", false, null, 0, listOf(UsageMetric("week", "", 100, null, 80))),
        ), null, 0)
        assertEquals("Codex esgotado", UsageText.headline(state))
        val calm = UsageState(true, listOf(UsageProvider("a", "Claude", "", false, null, 0, listOf(UsageMetric("session", "", 40, null, 50)))), null, 0)
        assertEquals("Claude 40%", UsageText.headline(calm))
    }
}
