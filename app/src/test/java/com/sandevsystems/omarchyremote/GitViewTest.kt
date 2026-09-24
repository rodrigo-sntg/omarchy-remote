package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.GitCommit
import com.sandevsystems.omarchyremote.network.GitFile
import com.sandevsystems.omarchyremote.network.GitSummary
import com.sandevsystems.omarchyremote.network.NetworkSession
import org.junit.Assert.assertEquals
import org.junit.Test

class GitViewTest {
    @Test
    fun theAgentsProjectInGitIsRead() {
        val summaries = mutableListOf<Pair<String, GitSummary>>()
        val diffs = mutableListOf<Triple<String, String, String>>()
        val s = NetworkSession(onAgentGit = { id, g -> summaries += id to g }, onAgentGitDiff = { id, path, diff -> diffs += Triple(id, path, diff) }) { true }
        s.onMessage("""{"type":"agent.git","id":"w1:p1","repo":true,"branch":"main","files":[{"path":"a.txt","state":"M","added":2,"removed":1}],"commits":[{"hash":"abc123","when":"2 hours ago","subject":"primeiro"}],"ahead":1,"behind":0}""", 0)
        s.onMessage("""{"type":"agent.git","id":"w2:p1","repo":false}""", 0)
        s.onMessage("""{"type":"agent.gitdiff","id":"w1:p1","path":"a.txt","diff":"-dois\n+DOIS"}""", 0)
        assertEquals("w1:p1" to GitSummary(true, "main", listOf(GitFile("a.txt", "M", 2, 1)), listOf(GitCommit("abc123", "2 hours ago", "primeiro")), 1, 0), summaries[0])
        assertEquals(false, summaries[1].second.repo)
        assertEquals(Triple("w1:p1", "a.txt", "-dois\n+DOIS"), diffs.single())
    }
}
