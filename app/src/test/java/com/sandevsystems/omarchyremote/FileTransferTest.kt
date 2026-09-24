package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.FileTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileTransferTest {
    @Test
    fun theFileAddressComesFromTheSessionAddress() {
        assertEquals("http://meu-pc.tail1234.ts.net:8765/v1/file", FileTransfer.httpUrl("ws://meu-pc.tail1234.ts.net:8765/v1", "file"))
        assertEquals("http://127.0.0.1:8765/v1/file/ab12", FileTransfer.httpUrl("ws://127.0.0.1:8765/v1", "file/ab12"))
        assertNull(FileTransfer.httpUrl("http://x/v1", "file"))
    }

    @Test
    fun namesArePercentEncodedAsUtf8() {
        // Python's urllib.parse.unquote reads %20 as a space but leaves "+" alone: no form encoding here.
        assertEquals("relat%C3%B3rio%20final%2Bv2.pdf", FileTransfer.encodeName("relatório final+v2.pdf"))
        assertEquals("a-b_c.~1.txt", FileTransfer.encodeName("a-b_c.~1.txt"))
    }

    @org.junit.Test
    fun aNameFromThePcNeverLeavesTheFolder() {
        assertEquals("x.xml", FileTransfer.safeName("../../../../data/data/com.sandevsystems.omarchyremote/shared_prefs/x.xml"))
        assertEquals("evil.txt", FileTransfer.safeName("..\\..\\evil.txt"))
        assertEquals("arquivo", FileTransfer.safeName(".."))
        assertEquals("arquivo", FileTransfer.safeName(""))
        assertEquals("env", FileTransfer.safeName(".env"))
        assertEquals("relatório.pdf", FileTransfer.safeName("relatório.pdf"))
        assertEquals(120, FileTransfer.safeName("a".repeat(500) + ".pdf").length)
        assertEquals("ab", FileTransfer.safeName("a\u0000b"))
    }
}
