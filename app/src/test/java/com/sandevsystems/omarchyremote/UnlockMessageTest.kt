package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.UnlockKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockMessageTest {
    @Test
    fun theSignedMessageNamesThePc() {
        val nonce = byteArrayOf(1, 2, 3)
        val expected = "omarchy-remote-unlock-v1\u0000pc.tail1234.ts.net\u0000".toByteArray() + nonce
        assertArrayEquals(expected, UnlockKey.message("pc.tail1234.ts.net", nonce))
    }

    @Test
    fun thePcsNameIsTheAddressAsTheHostSeesIt() {
        assertEquals("pc.tail1234.ts.net", UnlockKey.hostName(" PC.Tail1234.ts.net. "))
        assertEquals("pc.tail1234.ts.net", UnlockKey.hostName("pc.tail1234.ts.net:8765"))
    }
}
