package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.NetworkSession
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbsTest {
    @Test
    fun aMonitorsThumbnailIsAskedAndArrives() {
        val got = mutableListOf<Pair<String, ByteArray>>()
        val sent = mutableListOf<JSONObject>()
        val s = NetworkSession(onThumb = { m, b -> got += m to b }) { sent += JSONObject(it); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.thumbGet("DP-1", 480)
        assertEquals("thumb.get", sent.last().getString("type"))
        assertEquals(480, sent.last().getJSONObject("payload").getInt("width"))
        s.onMessage("""{"type":"thumb","monitor":"DP-1","jpeg":"/9hqcGVn"}""", 0)
        assertEquals("DP-1", got.single().first)
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 'j'.code.toByte(), 'p'.code.toByte(), 'e'.code.toByte(), 'g'.code.toByte()), got.single().second)
    }
}
