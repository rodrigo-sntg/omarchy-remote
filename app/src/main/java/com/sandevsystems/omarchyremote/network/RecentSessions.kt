package com.sandevsystems.omarchyremote.network

import org.json.JSONArray

/** An agent session the PC keeps (host sessions.py): [pane] while it runs in herdr, null once closed. */
data class RecentSession(
    val kind: String,
    val id: String,
    val title: String,
    val project: String,
    val account: String,
    val updated: Long,
    val pane: String?,
)

object RecentSessions {
    fun parse(items: JSONArray?): List<RecentSession> = (0 until (items?.length() ?: 0)).mapNotNull { i ->
        val o = items!!.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id").ifBlank { return@mapNotNull null }
        RecentSession(
            o.optString("kind"), id, o.optString("title"), o.optString("project"), o.optString("account"), o.optLong("updated"),
            if (o.optBoolean("open")) o.optString("pane").ifBlank { null } else null,
        )
    }

    /** The closed ones, newest first: what can be picked up again. */
    fun closed(list: List<RecentSession>, limit: Int = 4): List<RecentSession> =
        list.filter { it.pane == null }.sortedByDescending { it.updated }.take(limit)

    fun search(list: List<RecentSession>, query: String): List<RecentSession> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { q in it.title.lowercase() || q in it.project.lowercase() || q in it.account.lowercase() }
    }
}
