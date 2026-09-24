package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.PairUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairUriTest {
    private val code = "ABCD-EFGH-IJKL-MN23-QRS7" // base32, five groups of four (host/keypad_host/auth.py)

    @Test
    fun theQrFromTheOmarchyMenuFillsAddressAndCode() {
        assertEquals("meu-pc.tail1234.ts.net" to code, PairUri.parse("keypad://pair?host=meu-pc.tail1234.ts.net&code=$code"))
    }

    @Test
    fun anythingElseIsRefused() {
        assertNull(PairUri.parse("https://evil.example/?host=meu-pc.tail1234.ts.net&code=$code"))
        assertNull(PairUri.parse("keypad://pair?host=evil.example&code=$code"))
        assertNull(PairUri.parse("keypad://pair?host=meu-pc.tail1234.ts.net&code=abc"))
        assertNull(PairUri.parse("keypad://pair?host=meu-pc.tail1234.ts.net"))
        assertNull(PairUri.parse("keypad://other?host=meu-pc.tail1234.ts.net&code=$code"))
        assertNull(PairUri.parse("not a uri"))
    }

    @Test
    fun localhostOnlyWhenDebugAllowsIt() {
        assertNull(PairUri.parse("keypad://pair?host=localhost&code=$code", allowLoopback = false))
        assertEquals("localhost" to code, PairUri.parse("keypad://pair?host=localhost&code=$code", allowLoopback = true))
    }
}
