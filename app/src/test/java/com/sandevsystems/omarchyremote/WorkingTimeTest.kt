package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.WorkingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingTimeTest {
    @Test
    fun theClockGoesOnBetweenReadsOfTheScreen() {
        assertEquals("Trabalhando… 10m 20s · contexto 22%", WorkingTime.tick("Trabalhando… 10m 17s · contexto 22%", 3))
        assertEquals("Working… 1m 0s", WorkingTime.tick("Working… 59s", 1))
        assertEquals("Trabalhando… 1h 0m 5s", WorkingTime.tick("Trabalhando… 59m 59s", 6))
        assertEquals("Trabalhando… 2h 3m 4s", WorkingTime.tick("Trabalhando… 2h 3m 4s", 0))
    }

    @Test
    fun withoutAClockNothingChanges() {
        assertEquals("Trabalhando… Thinking", WorkingTime.tick("Trabalhando… Thinking", 5))
    }

    @Test
    fun onlyWorkingIsLive() {
        assertTrue(WorkingTime.isWorking("Trabalhando… 3s"))
        assertTrue(WorkingTime.isWorking("Working… 3s"))
        assertFalse(WorkingTime.isWorking("Trabalhou 3m 2s"))
        assertFalse(WorkingTime.isWorking(null))
    }
}
