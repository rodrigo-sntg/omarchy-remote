package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.HostInfo
import com.sandevsystems.omarchyremote.network.NetworkSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HostInfoTest {
    @Test
    fun theServiceSaysWhetherNewCodeWaits() {
        val got = mutableListOf<HostInfo>()
        val sent = mutableListOf<JSONObject>()
        val s = NetworkSession(onHost = { got += it }) { sent += JSONObject(it); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.hostGet()
        assertEquals("host.get", sent.last().getString("type"))
        s.onMessage("""{"type":"host","version":"a1b2c3d4e5","outdated":true}""", 0)
        assertEquals(HostInfo("a1b2c3d4e5", true), got.single())
    }
}
