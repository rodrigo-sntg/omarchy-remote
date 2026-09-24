package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.MirrorRules
import com.sandevsystems.omarchyremote.network.MirrorRules.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRulesTest {
    private val own = "com.sandevsystems.omarchyremote"
    private val msg = Candidate("com.whatsapp", ongoing = false, groupSummary = false, category = "msg", title = "Ana", text = "chego às 8")

    @Test
    fun aMessageGoesToThePc() = assertTrue(MirrorRules.send(msg, own, emptySet()))

    @Test
    fun whatNeverGoes() {
        assertFalse(MirrorRules.send(msg.copy(pkg = own), own, emptySet()))                  // our own
        assertFalse(MirrorRules.send(msg.copy(ongoing = true), own, emptySet()))             // music, downloads, services
        assertFalse(MirrorRules.send(msg.copy(groupSummary = true), own, emptySet()))        // "3 new messages"
        assertFalse(MirrorRules.send(msg.copy(category = "progress"), own, emptySet()))
        assertFalse(MirrorRules.send(msg.copy(category = "navigation"), own, emptySet()))
        assertFalse(MirrorRules.send(msg.copy(title = "", text = " "), own, emptySet()))      // nothing to read
        assertFalse(MirrorRules.send(msg, own, setOf("com.whatsapp")))                       // turned off for this app
    }

    @Test
    fun anUpdateWithTheSameWordsIsNotSentAgain() {
        val seen = mutableMapOf<String, Int>()
        assertTrue(MirrorRules.changed("k", msg, seen))
        assertFalse(MirrorRules.changed("k", msg, seen))
        assertTrue(MirrorRules.changed("k", msg.copy(text = "já cheguei"), seen))
    }

    @Test
    fun longTextsAreCut() {
        assertEquals(1500, MirrorRules.clip("x".repeat(5000), 1500).length)
        assertEquals("oi", MirrorRules.clip("oi", 1500))
    }
}

class MirrorAnswerTest {
    @org.junit.Test
    fun thePcMayOnlyAnswerWhatThePhoneSentIt() {
        val sent = setOf("k1")
        org.junit.Assert.assertTrue(MirrorRules.mayAnswer("k1", sent, enabled = true, pkg = "com.whatsapp", excluded = emptySet()))
        org.junit.Assert.assertFalse(MirrorRules.mayAnswer("k2", sent, enabled = true, pkg = "com.whatsapp", excluded = emptySet()))  // never sent
        org.junit.Assert.assertFalse(MirrorRules.mayAnswer("k1", sent, enabled = false, pkg = "com.whatsapp", excluded = emptySet())) // mirroring off
        org.junit.Assert.assertFalse(MirrorRules.mayAnswer("k1", sent, enabled = true, pkg = "com.whatsapp", excluded = setOf("com.whatsapp")))
    }
}
