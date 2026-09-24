package com.sandevsystems.omarchyremote.network

import org.json.JSONArray

/**
 * One entry of an agent's "/" menu (or Codex's "$" skills), as the PC lists it (host agent_commands.py):
 * [pt] is the Portuguese line of the CLI's own commands; [live] ones open a screen in the terminal.
 */
data class SlashCommand(val name: String, val description: String, val pt: String?, val group: String, val live: Boolean) {
    companion object {
        fun list(a: JSONArray?): List<SlashCommand> = if (a == null) emptyList() else (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SlashCommand(o.optString("n"), o.optString("d"), o.optString("pt").ifBlank { null }, o.optString("g"), o.optBoolean("live"))
        }
    }
}

private val trigger = Regex("^[/$][^\\s]*$")

/** The word being typed when the text is just "/…" or "$…": the menu's query; null otherwise. */
fun slashQuery(text: String): String? = text.takeIf { trigger.matches(it) }

/** What to show for [query]: names starting with it, then names holding it, then descriptions. */
fun slashMatches(items: List<SlashCommand>, query: String, limit: Int = 60): List<SlashCommand> {
    val mark = query.first()
    val word = query.drop(1).lowercase()
    val mine = items.filter { it.name.startsWith(mark) }
    if (word.isEmpty()) return mine.take(limit)
    val starts = mine.filter { it.name.lowercase().startsWith(query.lowercase()) }
    val inside = mine.filter { it !in starts && word in it.name.lowercase() }
    val said = mine.filter { it !in starts && it !in inside && (word in it.description.lowercase() || word in it.pt.orEmpty().lowercase()) }
    return (starts + inside + said).take(limit)
}
