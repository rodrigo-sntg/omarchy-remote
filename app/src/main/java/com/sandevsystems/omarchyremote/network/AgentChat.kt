package com.sandevsystems.omarchyremote.network

import org.json.JSONArray
import org.json.JSONObject

/** One thing in an agent's conversation, as the PC reads it from the session file (host transcript.py). */
sealed interface ChatItem {
    /** [ts]: when it was said (epoch seconds), if the session file says. */
    data class You(val text: String, val ts: Long? = null) : ChatItem
    data class Said(val text: String, val ts: Long? = null) : ChatItem
    /** An action; an edit brings [old]/[new] (a diff), Codex's patch [patch], a command [why] it runs. */
    data class Tool(
        val id: String, val name: String, val target: String,
        val why: String? = null, val old: String? = null, val new: String? = null, val patch: String? = null,
    ) : ChatItem
    /** An action's output: [cut] when only its start came; [at] is where the PC finds it whole. */
    data class Out(val id: String, val text: String, val cut: Boolean, val err: Boolean, val at: Long?) : ChatItem

    companion object {
        fun from(o: JSONObject): ChatItem? = when (o.optString("k")) {
            "you" -> You(o.optString("t"), if (o.has("ts")) o.getLong("ts") else null)
            "said" -> Said(o.optString("t"), if (o.has("ts")) o.getLong("ts") else null)
            "tool" -> Tool(o.optString("id"), o.optString("n"), o.optString("t"), o.optOrNull("why"), o.optOrNull("old"), o.optOrNull("new"), o.optOrNull("patch"))
            "out" -> Out(o.optString("id"), o.optString("t"), o.optBoolean("cut"), o.optBoolean("err"), if (o.has("at")) o.getLong("at") else null)
            else -> null
        }

        private fun JSONObject.optOrNull(name: String) = if (has(name) && !isNull(name)) getString(name) else null

        fun list(a: JSONArray?): List<ChatItem> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { from(a.getJSONObject(it)) }
    }
}

/** A page of the conversation: bytes [start, end) of the session file; [none] = no session file. */
data class AgentPage(val id: String, val items: List<ChatItem>, val start: Long, val end: Long, val more: Boolean, val none: Boolean)

/** What the chat shows: a message, or actions in a row with their outputs. */
sealed interface ChatBlock {
    val key: Long
    data class Message(override val key: Long, val item: ChatItem) : ChatBlock
    data class Step(val tool: ChatItem.Tool?, val out: ChatItem.Out?)
    data class Actions(override val key: Long, val steps: List<Step>) : ChatBlock
}

/**
 * The open agent's conversation: the pages read so far (older ones join on top, new messages at the
 * bottom) and the outputs fetched whole. Each entry keeps its [Entry.seq] for good, so the list
 * stays still while pages come in above and below.
 */
data class ChatState(
    val id: String,
    val entries: List<Entry> = emptyList(),
    val start: Long = -1,
    val end: Long = -1,
    val more: Boolean = false,
    val loading: Boolean = true,
    val none: Boolean = false,
    val outputs: Map<String, String> = emptyMap(),
) {
    data class Entry(val seq: Long, val item: ChatItem)

    val loaded get() = end >= 0

    fun page(p: AgentPage): ChatState = when {
        p.id != id -> this
        p.none -> copy(none = true, loading = false)
        !loaded -> copy(entries = p.items.mapIndexed { i, item -> Entry(i.toLong(), item) }, start = p.start, end = p.end, more = p.more, loading = false)
        p.end == start -> {
            val first = entries.firstOrNull()?.seq ?: 0L
            copy(entries = p.items.mapIndexed { i, item -> Entry(first - p.items.size + i, item) } + entries, start = p.start, more = p.more, loading = false)
        }
        else -> this  // a page that no longer fits (asked before a reload)
    }

    fun newItems(from: String, items: List<ChatItem>, newEnd: Long): ChatState {
        if (from != id || !loaded || items.isEmpty()) return if (from == id && loaded) copy(end = maxOf(end, newEnd)) else this
        val last = entries.lastOrNull()?.seq ?: -1L
        return copy(entries = entries + items.mapIndexed { i, item -> Entry(last + 1 + i, item) }, end = newEnd)
    }

    fun output(call: String, text: String) = copy(outputs = outputs + (call to text))

    fun blocks(): List<ChatBlock> {
        val out = mutableListOf<ChatBlock>()
        var steps = mutableListOf<ChatBlock.Step>()
        var key = 0L
        fun close() {
            if (steps.isNotEmpty()) out += ChatBlock.Actions(key, steps)
            steps = mutableListOf()
        }
        for (e in entries) {
            when (val item = e.item) {
                is ChatItem.Tool -> {
                    if (steps.isEmpty()) key = e.seq
                    steps += ChatBlock.Step(item, null)
                }
                is ChatItem.Out -> {
                    if (steps.isEmpty()) key = e.seq
                    val i = steps.indexOfLast { it.tool?.id == item.id && it.out == null }
                    if (i >= 0) steps[i] = steps[i].copy(out = item) else steps += ChatBlock.Step(null, item)
                }
                else -> {
                    close()
                    out += ChatBlock.Message(e.seq, item)
                }
            }
        }
        close()
        return out
    }
}
