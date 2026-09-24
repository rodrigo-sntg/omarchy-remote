package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.bluetooth.HidInputSession
import com.sandevsystems.omarchyremote.bluetooth.HidReports
import com.sandevsystems.omarchyremote.bluetooth.HidReports.KEYBOARD_ID
import com.sandevsystems.omarchyremote.bluetooth.HidReports.MOUSE_ID
import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.MouseButtons
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSessionTest {
    private class Recorder(var accept: (Int) -> Boolean = { true }) {
        val reports = mutableListOf<Pair<Int, ByteArray>>()
        fun send(id: Int, data: ByteArray): Boolean {
            reports += id to data
            return accept(reports.size)
        }
        val keyboard get() = reports.filter { it.first == KEYBOARD_ID }.map { it.second }
    }

    private val release = HidReports.keyboard()
    private fun press(usage: Int) = HidReports.keyboard(KeyStroke(usage))
    private val text = (0x04..0x1d).map { KeyStroke(it) } // a..z

    @Test
    fun tapSendsPressThenRelease() = runTest {
        val recorder = Recorder()
        val session = HidInputSession(recorder::send)
        assertTrue(session.tapKey(KeyStroke(0x04)))
        assertEquals(2, recorder.keyboard.size)
        assertArrayEquals(press(0x04), recorder.keyboard[0])
        assertArrayEquals(release, recorder.keyboard[1])
    }

    @Test
    fun concurrentTextsAreNotInterleaved() = runTest {
        val recorder = Recorder()
        val session = HidInputSession(recorder::send)
        launch { session.type(listOf(KeyStroke(0x04), KeyStroke(0x05))) }
        launch { session.type(listOf(KeyStroke(0x1e), KeyStroke(0x1f))) }
        advanceUntilIdle()
        val pressed = recorder.keyboard.filterNot { it.contentEquals(release) }.map { it[2].toInt() }
        assertEquals(listOf(0x04, 0x05, 0x1e, 0x1f), pressed)
    }

    @Test
    fun cancellingTextReleasesTheHeldKey() = runTest {
        val recorder = Recorder()
        val session = HidInputSession(recorder::send)
        val job = launch { session.type(text) }
        advanceTimeBy(HidInputSession.KEY_DELAY_MS * 4 + 1) // inside the third key hold
        job.cancel()
        advanceUntilIdle()
        assertArrayEquals(release, recorder.keyboard.last())
        assertTrue(recorder.keyboard.size < text.size * 2)
    }

    @Test
    fun rejectedReportStopsTheText() = runTest {
        val recorder = Recorder(accept = { it != 3 }) // third report (second press) fails
        val session = HidInputSession(recorder::send)
        val sent = session.type(text)
        assertEquals(1, sent)
        assertArrayEquals(release, recorder.keyboard.last())
        assertEquals(1, recorder.keyboard.count { it[2].toInt() == 0x05 })
        assertEquals(0, recorder.keyboard.count { it[2].toInt() == 0x06 })
    }

    @Test
    fun closedSessionDropsPendingTextAndNewSessionGetsNothingOld() = runTest {
        val old = Recorder()
        val oldSession = HidInputSession(old::send)
        val typing = async { oldSession.type(text) }
        advanceTimeBy(HidInputSession.KEY_DELAY_MS * 4 + 1)
        oldSession.close()
        val sizeAfterClose = old.reports.size
        val fresh = Recorder()
        HidInputSession(fresh::send)
        advanceUntilIdle()
        assertTrue(typing.await() < text.size)
        assertTrue(old.reports.drop(sizeAfterClose).all { it.second.all { byte -> byte.toInt() == 0 } })
        assertTrue(fresh.reports.isEmpty())
        assertFalse(oldSession.tapKey(KeyStroke(0x04)))
    }

    @Test
    fun dragKeepsButtonInMotionAndReleaseAllClearsEverything() = runTest {
        val recorder = Recorder()
        val session = HidInputSession(recorder::send)
        session.setMouseButtons(MouseButtons.LEFT)
        session.movePointer(200, 0)
        session.releaseAll()
        val mouse = recorder.reports.filter { it.first == MOUSE_ID }.map { it.second }
        assertEquals(listOf(1, 1, 1, 0), mouse.map { it[0].toInt() })
        assertEquals(200, mouse.sumOf { it[1].toInt() })
        assertArrayEquals(release, recorder.keyboard.last())
    }

    @Test
    fun scrollSendsWheelWithCurrentButtons() = runTest {
        val recorder = Recorder()
        val session = HidInputSession(recorder::send)
        session.scroll(-2)
        assertArrayEquals(HidReports.mouse(0, wheel = -2), recorder.reports.single().second)
    }

    @Test
    fun specialKeyWaitsForTextInProgress() = runTest {
        val recorder = Recorder()
        val session = HidInputSession(recorder::send)
        launch { session.type(listOf(KeyStroke(0x04), KeyStroke(0x05), KeyStroke(0x06))) }
        advanceTimeBy(1)
        launch { session.tapKey(KeyStroke(0x28)) } // Enter tapped mid-text
        advanceUntilIdle()
        val pressed = recorder.keyboard.filterNot { it.contentEquals(release) }.map { it[2].toInt() }
        assertEquals(listOf(0x04, 0x05, 0x06, 0x28), pressed)
    }
}
