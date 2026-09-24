package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Wake
import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeTest {
    private val json = JSONArray("""[
        {"name":"enp14s0","mac":"02:00:00:00:00:01","address":"192.168.1.3","broadcast":"192.168.1.255","wired":true,"wol":true},
        {"name":"wlp15s0","mac":"02:00:00:00:00:02","address":"192.168.2.72","broadcast":"192.168.2.255","wired":false,"wol":false}
    ]""")

    @Test
    fun theMagicPacketIsSixFfThenTheMacSixteenTimes() {
        val p = Wake.packet("02:00:00:00:00:01")
        assertEquals(102, p.size)
        assertArrayEquals(ByteArray(6) { 0xff.toByte() }, p.copyOfRange(0, 6))
        val mac = byteArrayOf(0x02, 0, 0, 0, 0, 0x01)
        for (i in 0 until 16) assertArrayEquals(mac, p.copyOfRange(6 + i * 6, 12 + i * 6))
    }

    @Test
    fun everyWayThePacketCanReachThePc() {
        val targets = Wake.targets(json)
        val sends = Wake.sends(targets)
        // each MAC, to the general broadcast, each LAN broadcast and each address, on ports 9 and 7
        val hosts = sends.map { it.host }.toSet()
        assertEquals(setOf("255.255.255.255", "192.168.1.255", "192.168.2.255", "192.168.1.3", "192.168.2.72"), hosts)
        assertEquals(setOf(7, 9), sends.map { it.port }.toSet())
        assertEquals(setOf("02:00:00:00:00:01", "02:00:00:00:00:02"), sends.map { it.mac }.toSet())
        assertEquals(sends.size, sends.toSet().size)
    }

    @Test
    fun whetherThePcIsSetUpForIt() {
        assertTrue(Wake.anyEnabled(Wake.targets(json)))
        assertFalse(Wake.anyEnabled(Wake.targets(JSONArray("""[{"mac":"aa:bb:cc:dd:ee:ff","address":"10.0.0.2","broadcast":"10.0.0.255","wol":false}]"""))))
        assertTrue(Wake.targets(JSONArray("""[{"mac":"bad","address":"10.0.0.2","broadcast":"10.0.0.255"}]""")).isEmpty())
    }

    @Test
    fun theTargetsSurviveBeingSaved() {
        val t = Wake.targets(json)
        assertEquals(t, Wake.targets(JSONArray(Wake.save(t))))
    }
}
