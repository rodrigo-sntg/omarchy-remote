package com.sandevsystems.omarchyremote.network

import org.json.JSONObject

/**
 * Agents talking to each other, as the PC follows them (host links.py): the exchanges going on or
 * just ended, and the notices waiting for someone (a finished review for the agent that asked).
 */
data class AgentLinks(val links: List<Link> = emptyList(), val notices: List<Notice> = emptyList()) {
    /** relay (a message passed on), review, or ask. [from] null: a plain terminal asked. */
    data class Link(val id: String, val kind: String, val from: String?, val to: String, val state: String, val since: Long) {
        val active: Boolean get() = state == "working" || state == "blocked"
    }

    data class Notice(val id: String, val kind: String, val origin: String?, val from: String, val fromKind: String, val text: String, val ts: Long)

    /** The agents [agent] is exchanging with right now. */
    fun talkingWith(agent: String): List<String> = links.filter { it.active }.mapNotNull {
        when (agent) {
            it.from -> it.to
            it.to -> it.from
            else -> null
        }
    }.distinct()

    /** The review [agent] asked for that is still going on. */
    fun reviewing(agent: String): Link? = links.lastOrNull { it.kind == "review" && it.from == agent && it.active }

    fun noticesFor(agent: String): List<Notice> = notices.filter { it.origin == agent }

    companion object {
        fun parse(message: JSONObject): AgentLinks {
            val links = message.optJSONArray("links")
            val notices = message.optJSONArray("notices")
            return AgentLinks(
                (0 until (links?.length() ?: 0)).mapNotNull { i ->
                    val o = links!!.optJSONObject(i) ?: return@mapNotNull null
                    Link(o.optString("id"), o.optString("kind"), if (o.isNull("from")) null else o.optString("from"), o.optString("to"),
                        o.optString("state"), o.optLong("since"))
                },
                (0 until (notices?.length() ?: 0)).mapNotNull { i ->
                    val o = notices!!.optJSONObject(i) ?: return@mapNotNull null
                    Notice(o.optString("id"), o.optString("kind"), if (o.isNull("origin")) null else o.optString("origin"), o.optString("from"),
                        o.optString("fromKind"), o.optString("text"), o.optLong("ts"))
                },
            )
        }
    }
}

/** The first line the PC puts on a prompt that came from another agent: "↪ Claude · app · w9:p2[ · review|ask]". */
data class RelayHeader(val kind: String, val project: String, val pane: String, val what: String, val body: String) {
    companion object {
        private val line = Regex("^↪ (\\S+) · (.+?) · (w\\S*:p\\d+)(?: · (review|ask))?$")

        fun parse(text: String): RelayHeader? {
            val first = text.substringBefore('\n')
            val m = line.matchEntire(first) ?: return null
            return RelayHeader(m.groupValues[1].lowercase(), m.groupValues[2], m.groupValues[3], m.groupValues[4].ifEmpty { "relay" },
                text.substringAfter('\n', "").trim())
        }
    }
}
