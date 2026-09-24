package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.ShareRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRulesTest {
    private val own = "com.sandevsystems.omarchyremote"

    @Test
    fun onlyOtherAppsContentGoes() {
        assertTrue(ShareRules.accepts("content", "com.android.providers.media.documents", own))
        assertFalse(ShareRules.accepts("file", "", own))                                          // file:///data/data/… of this app
        assertFalse(ShareRules.accepts("content", "$own.fileprovider", own))                    // this app's own files
        assertFalse(ShareRules.accepts("http", "evil.example", own))
        assertFalse(ShareRules.accepts(null, null, own))
    }

    @Test
    fun thePreviewIsShort() {
        assertEquals("abc", ShareRules.preview("abc"))
        assertEquals(201, ShareRules.preview("x".repeat(500)).length)
    }
}
