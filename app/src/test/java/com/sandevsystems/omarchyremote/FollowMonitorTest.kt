package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.followMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowMonitorTest {
    private val monitors = listOf("DP-1", "HDMI-A-1")

    @Test
    fun cursorThatWentToAnotherMonitorIsFollowed() {
        assertEquals("DP-1", followMonitor("DP-1", current = "HDMI-A-1", monitors = monitors, pending = null))
    }

    @Test
    fun cursorStillOnTheViewedMonitorChangesNothing() {
        assertNull(followMonitor(null, current = "HDMI-A-1", monitors = monitors, pending = null))
        assertNull(followMonitor("HDMI-A-1", current = "HDMI-A-1", monitors = monitors, pending = null))
    }

    @Test
    fun aSwitchAlreadyUnderwayIsNotRestarted() {
        assertNull(followMonitor("DP-1", current = "HDMI-A-1", monitors = monitors, pending = "DP-1"))
    }

    @Test
    fun unknownMonitorsAreIgnored() {
        assertNull(followMonitor("OMARCHYREMOTE", current = "HDMI-A-1", monitors = monitors, pending = null))
    }
}
