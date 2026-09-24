package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.bluetooth.HidReports
import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.ModifierKeys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportsTest {
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()

    @Test
    fun keyboardReportHasModifiersReservedAndFirstUsage() {
        val report = HidReports.keyboard(KeyStroke(0x04, ModifierKeys.CTRL or ModifierKeys.SHIFT))
        assertArrayEquals(bytes(0x03, 0, 0x04, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun keyboardReleaseIsAllZero() {
        assertArrayEquals(ByteArray(8), HidReports.keyboard())
    }

    @Test
    fun mouseReportMasksButtonsAndKeepsSignedAxes() {
        assertArrayEquals(bytes(0x01, 0xff, 0x05, 0x81), HidReports.mouse(0x09, dx = -1, dy = 5, wheel = -127))
    }

    @Test
    fun largeMotionIsSplitAndPreservesTotalAndButtons() {
        val reports = HidReports.mouseMotion(buttons = 1, dx = 300, dy = -200)
        assertEquals(300, reports.sumOf { it[1].toInt() })
        assertEquals(-200, reports.sumOf { it[2].toInt() })
        assertTrue(reports.all { it[0].toInt() == 1 })
        assertTrue(reports.all { it[1] in -127..127 && it[2] in -127..127 })
    }

    @Test
    fun zeroMotionProducesNoReport() {
        assertTrue(HidReports.mouseMotion(buttons = 0, dx = 0, dy = 0).isEmpty())
    }

    @Test
    fun capsLockIsReadFromLedOutputWithOrWithoutReportId() {
        assertTrue(HidReports.capsLock(bytes(0x02)))
        assertTrue(HidReports.capsLock(bytes(HidReports.KEYBOARD_ID, 0x02)))
        assertFalse(HidReports.capsLock(bytes(0x01)))
        assertFalse(HidReports.capsLock(bytes(HidReports.KEYBOARD_ID, 0x01)))
        assertFalse(HidReports.capsLock(ByteArray(0)))
    }

    @Test
    fun descriptorDeclaresKeyboardAndMouseReportIds() {
        val d = HidReports.descriptor.map { it.toInt() and 0xff }
        val reportIds = d.indices.filter { d[it] == 0x85 }.map { d[it + 1] }
        assertEquals(listOf(HidReports.KEYBOARD_ID, HidReports.MOUSE_ID), reportIds)
    }
}
