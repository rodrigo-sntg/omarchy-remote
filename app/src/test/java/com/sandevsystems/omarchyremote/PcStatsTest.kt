package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.PcStats
import org.junit.Assert.assertEquals
import org.junit.Test

class PcStatsTest {
    @Test
    fun howThePcIsDoingIsRead() {
        val got = mutableListOf<PcStats>()
        val s = NetworkSession(onStats = { got += it }) { true }
        s.onMessage("""{"type":"stats","cpu":12,"mem_used":34.0,"mem_total":64.9,"temp":55,"battery":null,"charging":false,"uptime":19307}""", 0)
        assertEquals(PcStats(12, 34.0, 64.9, 55, null, false, 19307), got.single())
    }
}
