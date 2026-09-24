package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.TermLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermLinksTest {
    @Test
    fun thePageNeverLeavesTheBundledTerminal() {
        assertTrue(TermLinks.staysInPage("file:///android_asset/term/index.html"))
        assertFalse(TermLinks.staysInPage("https://evil.example/"))
        assertFalse(TermLinks.staysInPage("file:///android_asset/term/../other.html"))
        assertFalse(TermLinks.staysInPage("file:///sdcard/x.html"))
        assertFalse(TermLinks.staysInPage("javascript:alert(1)"))
    }

    @Test
    fun onlyWebLinksOpenOutside() {
        assertEquals("https://github.com/x", TermLinks.external("https://github.com/x"))
        assertEquals("http://localhost:3000/", TermLinks.external("http://localhost:3000/"))
        assertNull(TermLinks.external("file:///etc/passwd"))
        assertNull(TermLinks.external("intent://scan#Intent;end"))
        assertNull(TermLinks.external("javascript:alert(1)"))
    }
}
