package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.KeypadApp.ClipSend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcClipboardTest {
    @Test
    fun pastesOnceThePcHasThePhonesCopy() {
        assertTrue(PcClipboard.afterSend(ClipSend.SENT).paste)
        assertNull(PcClipboard.afterSend(ClipSend.SENT).message)
    }

    @Test
    fun withNothingNewOnThePhoneItPastesWhatThePcHas() {
        for (result in listOf(ClipSend.NOTHING, ClipSend.UNREADABLE)) {
            assertTrue(PcClipboard.afterSend(result).paste)
        }
    }

    @Test
    fun neverPastesSomethingOldWhenTheCopyDidNotArrive() {
        for (result in listOf(ClipSend.TOO_LONG, ClipSend.OFFLINE, ClipSend.FAILED)) {
            val outcome = PcClipboard.afterSend(result)
            assertFalse(outcome.paste)
            assertTrue(outcome.message!!.isNotBlank())
        }
    }
}
