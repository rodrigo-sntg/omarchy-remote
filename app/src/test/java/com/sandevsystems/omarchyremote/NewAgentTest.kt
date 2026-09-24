package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAgentTest {
    @Test
    fun theProjectsComeAndAnAgentStartsInOne() = runTest {
        val sent = mutableListOf<JSONObject>()
        val projects = mutableListOf<List<Project>>()
        val started = mutableListOf<String>()
        val s = NetworkSession(onProjects = { projects += it }, onAgentStarted = { started += it }) { sent += JSONObject(it); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.listProjects()
        assertEquals("projects.list", sent.last().getString("type"))
        s.onMessage("""{"type":"projects","items":[{"path":"/home/u/app","name":"app","workspace":"w1"},{"path":"/home/u/site","name":"site","workspace":null}]}""", 0)
        assertEquals(listOf(Project("/home/u/app", "app", "w1"), Project("/home/u/site", "site", null)), projects.single())
        val ok = async { s.startAgent("/home/u/app", "claude", "roda os testes") }
        runCurrent()
        val start = sent.last()
        assertEquals("agent.start", start.getString("type"))
        assertEquals("/home/u/app", start.getJSONObject("payload").getString("cwd"))
        assertEquals("roda os testes", start.getJSONObject("payload").getString("prompt"))
        s.onMessage("""{"type":"ack","seq":${start.getInt("seq")},"ok":true}""", 0)
        s.onMessage("""{"type":"agent.started","id":"w1:p7"}""", 0)
        assertTrue(ok.await())
        assertEquals(listOf("w1:p7"), started)
    }
}
