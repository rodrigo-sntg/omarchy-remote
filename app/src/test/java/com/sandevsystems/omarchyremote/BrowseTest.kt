package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.FolderListing
import com.sandevsystems.omarchyremote.network.NetworkSession
import com.sandevsystems.omarchyremote.network.PcFile
import com.sandevsystems.omarchyremote.network.firstLink
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseTest {
    @Test
    fun aFolderOfThePcIsRead() {
        val got = mutableListOf<FolderListing>()
        val sent = mutableListOf<JSONObject>()
        val s = NetworkSession(onFiles = { got += it }) { sent += JSONObject(it); true }
        s.onMessage("""{"type":"session","sessionId":"abc"}""", 0)
        s.filesList("/home/u/Downloads")
        assertEquals("/home/u/Downloads", sent.last().getJSONObject("payload").getString("path"))
        s.onMessage("""{"type":"files","path":"/home/u/Downloads","parent":"/home/u","items":[{"name":"a","dir":true,"size":0,"mtime":5},{"name":"b.pdf","dir":false,"size":12,"mtime":9}],"roots":[{"path":"/home/u/Downloads","name":"Downloads"}]}""", 0)
        assertEquals(
            FolderListing("/home/u/Downloads", "/home/u", listOf(PcFile("a", true, 0, 5), PcFile("b.pdf", false, 12, 9)), listOf("/home/u/Downloads" to "Downloads")),
            got.single(),
        )
    }

    @Test
    fun theLinkInASharedTextIsFound() {
        assertEquals("https://github.com/x/y", firstLink("Olha isso: https://github.com/x/y muito bom"))
        assertEquals("http://a.b/c?d=1", firstLink("http://a.b/c?d=1"))
        assertNull(firstLink("sem link aqui"))
    }
}
