package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.TailnetDns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class TailnetDnsTest {
    private fun ip(s: String) = InetAddress.getByName(s)

    @Test
    fun onlyTailscaleAddressesCount() {
        assertTrue(TailnetDns.isTailnet(ip("100.64.0.1"), loopback = false))
        assertTrue(TailnetDns.isTailnet(ip("100.127.255.254"), loopback = false))
        assertTrue(TailnetDns.isTailnet(ip("fd7a:115c:a1e0::1"), loopback = false))
        assertFalse(TailnetDns.isTailnet(ip("100.63.255.255"), loopback = false))   // just below the range
        assertFalse(TailnetDns.isTailnet(ip("100.128.0.1"), loopback = false))
        assertFalse(TailnetDns.isTailnet(ip("192.168.1.10"), loopback = false))     // a Wi-Fi's answer
        assertFalse(TailnetDns.isTailnet(ip("8.8.8.8"), loopback = false))
        assertFalse(TailnetDns.isTailnet(ip("127.0.0.1"), loopback = false))
        assertTrue(TailnetDns.isTailnet(ip("127.0.0.1"), loopback = true))           // debug builds (adb reverse)
    }

    @Test
    fun aSpoofedAnswerIsRefusedBeforeAnythingIsSent() {
        val dns = TailnetDns(loopback = false) { listOf(ip("203.0.113.9")) }
        val refused = runCatching { dns.lookup("pc.tail1234.ts.net") }.exceptionOrNull()
        assertTrue(refused is UnknownHostException)
        val ok = TailnetDns(loopback = false) { listOf(ip("203.0.113.9"), ip("100.100.1.2")) }
        assertEquals(listOf(ip("100.100.1.2")), ok.lookup("pc.tail1234.ts.net"))
    }
}
