package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.BackgroundLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLinkTest {
    @Test
    fun reconnectsOnlyWhenDisconnectedAndTheLastTryIsOldEnough() {
        assertFalse(BackgroundLink.reconnectDue(connected = true, lastTryMs = 0, nowMs = 100_000))
        assertFalse(BackgroundLink.reconnectDue(connected = false, lastTryMs = 90_000, nowMs = 100_000))
        assertTrue(BackgroundLink.reconnectDue(connected = false, lastTryMs = 60_000, nowMs = 100_000))
        assertTrue(BackgroundLink.reconnectDue(connected = false, lastTryMs = 0, nowMs = 0)) // first try, right away
    }

    @Test
    fun anExplicitDisconnectHoldsTheLinkUntilTheNextConnect() {
        assertFalse(BackgroundLink.reconnectDue(connected = false, lastTryMs = 0, nowMs = 100_000, held = true))
    }
}

class StatusTextTest {
    @Test
    fun onlyAChangedTextIsPosted() {
        val status = com.sandevsystems.omarchyremote.network.StatusText()
        assertTrue(status.offer("Ligado a meu-pc · 2 agentes"))
        assertFalse(status.offer("Ligado a meu-pc · 2 agentes"))
        assertTrue(status.offer("Ligado a meu-pc · 2 agentes · 1 esperando você"))
        status.reset()
        assertTrue(status.offer("Ligado a meu-pc · 2 agentes · 1 esperando você"))
    }

    @Test
    fun aDroppedSessionIsRetriedSoonThenLessOften() {
        // The PC's host restarting takes a couple of seconds: the first tries come quickly.
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..5).map(BackgroundLink::retryDelayMs))
    }

    @Test
    fun aFreshPairingWithoutASavedTransportIsTheNetwork() {
        // The app's screen treats a missing choice as the network; the background link must too.
        assertTrue(BackgroundLink.overNetwork(null))
        assertTrue(BackgroundLink.overNetwork("NETWORK"))
        assertFalse(BackgroundLink.overNetwork("BLUETOOTH"))
    }
}
