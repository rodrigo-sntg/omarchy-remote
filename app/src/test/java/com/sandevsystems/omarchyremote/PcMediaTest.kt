package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.PcMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcMediaTest {
    private val playing = PcMedia(true, "Night Drive", "Demo Beats", "mpv")

    @Test
    fun theLockScreenShowsItOnlyWhileConnectedAndSomethingIsLoaded() {
        assertTrue(playing.show(connected = true, enabled = true))
        assertTrue(playing.copy(playing = false).show(connected = true, enabled = true))  // paused: still there
        assertFalse(playing.show(connected = false, enabled = true))
        assertFalse(playing.show(connected = true, enabled = false))
        assertFalse(PcMedia(false, "", "", "").show(connected = true, enabled = true))    // the player closed
    }

    @Test
    fun theSubtitleSaysWhereItPlays() {
        assertEquals("No PC · Mpv", playing.where())
        assertEquals("No PC", playing.copy(player = "").where())
        assertEquals("No PC · Spotify", playing.copy(player = "spotify.instance123").where())
    }

    @Test
    fun theMessageIsRead() {
        val got = mutableListOf<PcMedia>()
        val s = NetworkSession(onMedia = { got += it }) { true }
        s.onMessage("""{"type":"media","playing":true,"artist":"Demo Beats","title":"Night Drive","player":"mpv"}""", 0)
        assertEquals(playing, got.single())
    }
}
