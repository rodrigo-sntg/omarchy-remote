package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.NetworkAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkAddressTest {
    @Test
    fun tailnetNamesBecomeWebSocketUrls() {
        assertEquals("ws://meu-pc.tail1234.ts.net:8765/v1", NetworkAddress.url("meu-pc.tail1234.ts.net"))
        assertEquals("ws://meu-pc.tail1234.ts.net:9000/v1", NetworkAddress.url(" MEU-PC.tail1234.ts.net:9000 "))
    }

    @Test
    fun onlyTailnetNamesAreAccepted() {
        // Cleartext is only allowed for *.ts.net, where WireGuard encrypts the traffic.
        assertNull(NetworkAddress.url("meu-pc"))
        assertNull(NetworkAddress.url("100.64.0.10"))
        assertNull(NetworkAddress.url("192.168.0.10"))
        assertNull(NetworkAddress.url("evil-ts.net"))
        assertNull(NetworkAddress.url("meu-pc.tail1234.ts.net:99999"))
        assertNull(NetworkAddress.url("ws://meu-pc.tail1234.ts.net"))
        assertNull(NetworkAddress.url(""))
    }

    @Test
    fun loopbackIsOnlyForDebugBuildsTestingThroughAdbReverse() {
        assertNull(NetworkAddress.url("localhost"))
        assertEquals("ws://127.0.0.1:8765/v1", NetworkAddress.url("localhost", allowLoopback = true))
        assertEquals("ws://127.0.0.1:9000/v1", NetworkAddress.url("LOCALHOST:9000", allowLoopback = true))
        assertNull(NetworkAddress.url("localhost.evil.com", allowLoopback = true))
        assertEquals("ws://meu-pc.tail1234.ts.net:8765/v1", NetworkAddress.url("meu-pc.tail1234.ts.net", allowLoopback = true))
    }
}
