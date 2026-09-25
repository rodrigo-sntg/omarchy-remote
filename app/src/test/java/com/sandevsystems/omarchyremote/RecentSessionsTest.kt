package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.RecentSession
import com.sandevsystems.omarchyremote.network.RecentSessions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSessionsTest {
    private val json = JSONArray()
        .put(JSONObject().put("kind", "claude").put("id", "a").put("title", "TASK-12 invoices").put("cwd", "/home/u/nf").put("project", "nf")
            .put("account", "studio").put("updated", 2000).put("open", false))
        .put(JSONObject().put("kind", "claude").put("id", "b").put("title", "Aberta").put("cwd", "/home/u/app").put("project", "app")
            .put("account", "").put("updated", 1900).put("open", true).put("pane", "w5:p3"))
        .put(JSONObject().put("kind", "codex").put("id", "c").put("title", "Velha").put("cwd", "/home/u/x").put("project", "x")
            .put("account", "").put("updated", 100).put("open", false))

    @Test
    fun readsWhatThePcSends() {
        val list = RecentSessions.parse(json)
        assertEquals(RecentSession("claude", "a", "TASK-12 invoices", "nf", "studio", 2000, null), list[0])
        assertEquals("w5:p3", list[1].pane)
        assertNull(list[2].pane)
    }

    @Test
    fun closedOnesNewestFirstForTheAgentsTab() {
        val closed = RecentSessions.closed(RecentSessions.parse(json), limit = 1)
        assertEquals(listOf("a"), closed.map { it.id })
    }

    @Test
    fun searchLooksAtTitleProjectAndAccount() {
        val list = RecentSessions.parse(json)
        assertEquals(listOf("a"), RecentSessions.search(list, "stud").map { it.id })
        assertEquals(listOf("a"), RecentSessions.search(list, " Invoices ").map { it.id })
        assertTrue(RecentSessions.search(list, "").size == 3)
    }
}
