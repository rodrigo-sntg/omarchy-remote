package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.PalmGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalmGuardTest {
    @Test
    fun fingersAreIgnoredWhileThePenIsNearAndShortlyAfter() {
        val guard = PalmGuard()
        assertFalse(guard.blocksFingers(0))
        guard.pen("hover", 1_000)
        assertTrue(guard.blocksFingers(5_000))  // hovering: the hand rests on the screen
        guard.pen("move", 6_000)
        guard.pen("out", 6_100)
        assertTrue(guard.blocksFingers(6_100 + PalmGuard.GRACE_MS - 1))
        assertFalse(guard.blocksFingers(6_100 + PalmGuard.GRACE_MS + 1))
    }
}
