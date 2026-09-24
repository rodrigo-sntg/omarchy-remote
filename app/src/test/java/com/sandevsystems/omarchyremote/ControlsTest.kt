package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Controls
import com.sandevsystems.omarchyremote.network.NetworkSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlsTest {
    @Test
    fun thePcsTogglesAreReadAndSet() {
        val got = mutableListOf<Controls>()
        val sent = mutableListOf<JSONObject>()
        val s = NetworkSession(onControls = { got += it }) { sent += JSONObject(it); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.onMessage("""{"type":"controls","volume":45,"muted":true,"mic_muted":false,"output":"Headset","nightlight":true,"awake":false,"dnd":true,"recording":false,"power":"balanced","powers":["power-saver","balanced"],"bluetooth":true}""", 0)
        assertEquals(Controls(45, true, false, "Headset", true, false, true, false, "balanced", listOf("power-saver", "balanced"), true), got.single())
        s.controlsGet()
        assertEquals("controls.get", sent.last().getString("type"))
    }
}
