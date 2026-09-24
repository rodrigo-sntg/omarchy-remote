package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.VideoQuality
import com.sandevsystems.omarchyremote.display.VideoStats
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoQualityTest {
    @Test
    fun presetsTradeSharpnessForBandwidth() {
        assertEquals(60 to 1.0f, VideoQuality.SHARP.fps to VideoQuality.SHARP.scale)
        assertEquals(30 to 0.75f, VideoQuality.BALANCED.fps to VideoQuality.BALANCED.scale)
        assertEquals(20 to 0.5f, VideoQuality.LIGHT.fps to VideoQuality.LIGHT.scale)
        assertEquals(VideoQuality.BALANCED, VideoQuality.from("BALANCED"))
        assertEquals(VideoQuality.SHARP, VideoQuality.from(null))
    }

    @Test
    fun theChipShowsRoundTripAndFramesPerSecond() {
        val stats = VideoStats()
        stats.frame(1_000); stats.frame(1_016); stats.frame(1_033)
        stats.pong(sentMs = 2_000, nowMs = 2_048)
        // Numbers mean nothing to the person: a good link says nothing.
        assertEquals("", stats.text(nowMs = 2_000))
        stats.pong(sentMs = 3_000, nowMs = 3_020) // smoothed, not jumpy
        assertEquals(41, stats.rttMs)
        assertEquals("", VideoStats().text(nowMs = 0))
        // A slow link is said in words.
        val slow = VideoStats().apply { pong(0, 400) }
        assertEquals("rede lenta", slow.text(nowMs = 5_000))
    }
}
