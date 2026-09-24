package com.sandevsystems.omarchyremote.network

/**
 * A numbered picker on an agent's screen (Claude Code's /model, /resume…, Codex's model and effort)
 * as a list the phone can show natively: pressing an option's number, then Enter, picks it. A
 * question ending in "?" is a permission request, not a picker (AgentConversation reads those).
 */
data class TuiPicker(val title: String, val options: List<Option>, val hint: String?, val cursor: Int = 0) {
    data class Option(val key: String, val label: String, val detail: String, val current: Boolean)

    /** The keys that take the cursor to option [index] and pick it: arrows, then Enter (whatever a number key does). */
    fun keysTo(index: Int): List<String> {
        val moves = index - cursor
        return List(kotlin.math.abs(moves)) { if (moves > 0) "down" else "up" } + "enter"
    }

    companion object {
        private val option = Regex("^(\\s*)([❯›>▶]\\s*)?(\\d{1,2})\\.\\s+(.+?)\\s*$")
        private val columns = Regex("^(.*?)(?:\\s{2,}(.*))?$")
        private val marks = Regex("\\s*(✔|✓|\\(current\\))\\s*")

        fun parse(raw: String): TuiPicker? {
            val all = raw.lines().map { it.trimEnd() }
            val from = maxOf(0, all.size - 45)
            val lines = all.subList(from, all.size)
            val first = lines.indexOfFirst { option.matches(it) }
            if (first < 0) return null
            val options = mutableListOf<Option>()
            var i = first
            var detailColumn = Int.MAX_VALUE
            var cursor = 0
            while (i < lines.size) {
                val line = lines[i]
                val m = option.find(line)
                if (m != null) {
                    if (m.groupValues[2].isNotEmpty()) cursor = options.size
                    val body = m.groupValues[4]
                    val cols = columns.find(body)!!
                    val labelRaw = cols.groupValues[1]
                    val current = marks.containsMatchIn(labelRaw) || marks.containsMatchIn(body.substringAfter(labelRaw).take(12))
                    val detail = cols.groupValues[2].replace(marks, " ").trim()
                    options += Option(m.groupValues[3], labelRaw.replace(marks, " ").trim(), detail, current)
                    detailColumn = if (detail.isNotEmpty()) line.indexOf(cols.groupValues[2]) else Int.MAX_VALUE
                } else if (line.isNotBlank() && line.length - line.trimStart().length >= detailColumn - 2 && options.isNotEmpty()) {
                    val last = options.removeAt(options.size - 1)
                    options += last.copy(detail = (last.detail + " " + line.trim()).trim())
                } else break
                i++
            }
            if (options.size < 2) return null
            // The block of text right above the options: its first line is the title; a question is a request.
            var top = first - 1
            while (top >= 0 && lines[top].isBlank()) top--
            if (top < 0 || lines[top].trim().endsWith("?")) return null
            var start = top
            while (start > 0 && lines[start - 1].isNotBlank() && lines[start - 1].trim().first() !in "─━▔▁╭╮╰╯│") start--
            val title = lines[start].trim()
            val hint = lines.drop(i).firstOrNull { it.isNotBlank() && ("enter" in it.lowercase() || "esc" in it.lowercase()) }?.trim()
            return TuiPicker(title, options, hint, cursor)
        }

        private val claudeStatus = Regex("\\|\\s*([A-Z][^|]*?)(?:\\s+ctx\\s+\\d+%)?\\s*$")
        private val codexStatus = Regex("^\\s*([a-z][\\w.-]*\\d[\\w.-]*(?:\\s+(?:minimal|low|medium|high|xhigh))?)\\b(?:\\s+fast)?\\s+·")

        /** The model the agent runs, as its status line says. */
        fun model(raw: String, kind: String): String? {
            val lines = raw.lines().asReversed()
            return when (kind) {
                "claude" -> lines.firstNotNullOfOrNull { l -> if ("|" in l) claudeStatus.find(l)?.groupValues?.get(1)?.trim() else null }
                "codex" -> lines.firstNotNullOfOrNull { l -> codexStatus.find(l)?.groupValues?.get(1)?.trim() }
                else -> null
            }
        }
    }
}
