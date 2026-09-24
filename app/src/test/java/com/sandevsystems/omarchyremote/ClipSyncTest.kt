package com.sandevsystems.omarchyremote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipSyncTest {
    @Test
    fun aPcCopyIsSetOnThePhoneOnce() {
        val sync = ClipSync()
        assertTrue(sync.fromPc("do PC"))
        assertFalse(sync.fromPc("do PC"))
        assertFalse(sync.fromPc(""))
    }

    @Test
    fun whatCameFromThePcIsNotSentBack() {
        val sync = ClipSync()
        sync.fromPc("do PC")
        assertFalse(sync.toPc("do PC"))
    }

    @Test
    fun aNewPhoneCopyGoesToThePcUntilItIsSent() {
        val sync = ClipSync()
        assertTrue(sync.toPc("do celular"))
        assertTrue(sync.toPc("do celular"))  // not sent yet (no connection): try again next time
        sync.sent("do celular")
        assertFalse(sync.toPc("do celular"))
        assertFalse(sync.fromPc("do celular"))  // the PC's watcher echoes it: nothing to set
    }

    @Test
    fun blankOrTooLongStaysOnThePhone() {
        val sync = ClipSync(limit = 5)
        assertFalse(sync.toPc("   "))
        assertFalse(sync.toPc("123456"))
    }

    @Test
    fun theClipboardIsReadOnlyWhenItChanged() {
        val sync = ClipSync()
        assertTrue(sync.changed(100))
        sync.looked(100)
        assertFalse(sync.changed(100))
        assertTrue(sync.changed(200))
    }

    @Test
    fun whatWasLookedAtSurvivesARestart() {
        val saved = mutableListOf<Long>()
        val before = ClipSync(onLooked = { saved += it })
        before.looked(500)
        val after = ClipSync(lastLooked = saved.last())
        assertFalse(after.changed(500))  // the same copy is not sent again after the app restarts
        assertTrue(after.changed(600))
    }
}
