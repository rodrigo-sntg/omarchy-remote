package com.sandevsystems.omarchyremote.network

/**
 * Claude Code's screen read as a conversation (design 5C/5D): what the person said (`❯`), what the
 * agent said and did (`●`/`⏺`, a tool call being `Name(target)` with its `⎿` output under it), and a
 * permission request with its numbered options. Anything else (Codex, a shell) is not a
 * conversation: null, and the screen shows as it is.
 */
object AgentConversation {
    sealed interface Item
    data class You(val text: String) : Item
    data class Said(val text: String) : Item
    data class Did(val tool: String, val target: String) : Item

    enum class Choice { YES, ALWAYS, NO, OTHER }
    data class Option(val key: String, val label: String, val choice: Choice)
    data class Ask(val title: String, val detail: String, val question: String, val options: List<Option>)
    data class Chat(val items: List<Item>, val ask: Ask?, val footer: String?)

    private val said = Regex("^[●⏺] (.*)$")
    private val you = Regex("^[❯>] (.+)$")
    private val toolCall = Regex("^([A-Z][\\w-]*(?: [A-Z][\\w-]*)*(?: \\(MCP\\))?)\\((.*)$")
    private val summary = Regex("^(?:Read|Ran|Searched for|Edited|Wrote|Updated|Listed|Fetched|Created|Found|Running) \\d+ ")
    /** A whole line of Claude Code's collapsed actions, as it also shows them without the ● mark. */
    private val summaryLine = Regex(
        "^(?:Read|Ran|Searched for|Edited|Wrote|Updated|Listed|Fetched|Created|Found|Running) \\d+ [a-z ]+" +
            "(?:, (?:read|ran|searched for|edited|wrote|updated|listed|fetched|created|found) \\d+ [a-z ]+)*(?:…|\\.\\.\\.)?(?: \\(ctrl\\+o to expand\\))?$",
    )
    private val status = Regex("^[✻✶✳✢·*✽※] ")
    private val option = Regex("^(?:❯\\s*)?(\\d+)\\.\\s+(.+)$")
    private val boxChars = Regex("[│┌┐└┘├┤┬┴┼─━╭╮╰╯║═╌╍┄┅┈┉]")
    private val onlyBox = Regex("^[\\s│┌┐└┘├┤┬┴┼─━╭╮╰╯║═╌╍┄┅┈┉]*$")
    private val inputRule = Regex("^\\s*[─━]{10,}\\s*$")
    private val listMark = Regex("^(?:[-*•] |\\d+[.)] )")

    fun parse(raw: String): Chat? {
        val all = raw.lines().map { it.trimEnd() }
        val found = findAsk(all)
        val above = all.subList(0, found?.second ?: all.size)
        // Only the input box is cut here (a table can end a message: it is no chrome); the footer's
        // facts (time worked, context) come from the screen's usual reading.
        val boxTop = above.indices.drop(maxOf(0, above.size - 25)).firstOrNull { inputRule.matches(above[it]) }
        val lines = if (boxTop != null) above.subList(0, boxTop) else above
        if (lines.none { said.matches(it) }) return null
        return Chat(items(lines), found?.first, AgentScreen.clean(above.joinToString("\n")).footer)
    }

    private enum class Mode { NONE, SAID, YOU, SKIP }

    private fun items(lines: List<String>): List<Item> {
        val out = mutableListOf<Item>()
        var mode = Mode.NONE
        val buf = mutableListOf<String>()
        fun flush() {
            val text = join(buf)
            if (text.isNotEmpty()) out += if (mode == Mode.YOU) You(text) else Said(text)
            buf.clear()
        }
        for (line in lines) {
            val indent = line.length - line.trimStart().length
            when {
                line.isBlank() -> if (mode == Mode.SAID || mode == Mode.YOU) buf += ""
                said.matches(line) -> {
                    flush()
                    val text = said.find(line)!!.groupValues[1].trim()
                    val call = toolCall.find(text)
                    if (call != null) {
                        out += Did(call.groupValues[1], call.groupValues[2].removeSuffix(")"))
                        mode = Mode.SKIP
                    } else if (summary.containsMatchIn(text) || "(ctrl+o to expand)" in text || text.startsWith("Background command ")) {
                        // Claude Code's collapsed actions: "Read 1 file, ran 3 shell commands".
                        out += Did("", text.replace("(ctrl+o to expand)", "").trim())
                        mode = Mode.SKIP
                    } else {
                        mode = Mode.SAID
                        buf += text
                    }
                }
                you.matches(line) -> {
                    flush()
                    mode = Mode.YOU
                    buf += you.find(line)!!.groupValues[1].trim()
                }
                indent in 1..4 && summaryLine.matches(line.trim()) -> {
                    flush()
                    out += Did("", line.trim().replace("(ctrl+o to expand)", "").trim())
                    mode = Mode.SKIP
                }
                indent == 0 || status.containsMatchIn(line) -> {
                    flush()
                    mode = Mode.SKIP  // a status line, a tip, a banner: not part of the conversation
                }
                line.trimStart().startsWith("⎿") -> {
                    // Output right under a one-line message: that "message" was an action
                    // ("Read 2 files", "Running …").
                    if (mode == Mode.SAID && buf.size == 1) {
                        out += Did("", buf.single().removeSuffix("(ctrl+o to expand)").trim())
                        buf.clear()
                    }
                    flush()
                    mode = Mode.SKIP
                }
                mode == Mode.SAID || mode == Mode.YOU -> buf += line.drop(2)
                // The screen starts mid-message: its tail is still the agent's words (a tool's output
                // sits deeper).
                mode == Mode.NONE && indent < 5 -> {
                    mode = Mode.SAID
                    buf += line.drop(2)
                }
            }
        }
        flush()
        return out
    }

    /** Terminal lines back into text: wrapped lines rejoined; paragraphs, list items and tables kept. */
    private fun join(lines: List<String>): String {
        val sb = StringBuilder()
        var previous: String? = null
        for (line in lines.dropLastWhile { it.isBlank() }) {
            val boxed = boxChars.containsMatchIn(line)
            when {
                line.isBlank() -> sb.append("\n")
                previous == null -> sb.append(line.trim())
                previous.isBlank() -> sb.append("\n").append(if (boxed) line.trimEnd() else line.trim())
                boxed || boxChars.containsMatchIn(previous) || listMark.containsMatchIn(line.trimStart()) && !line.startsWith("  ") ->
                    sb.append("\n").append(if (boxed) line.trimEnd() else line.trim())
                else -> sb.append(" ").append(line.trim())
            }
            previous = line
        }
        return sb.toString().trim()
    }

    /** The permission request at the bottom, and the line its box starts on. */
    private fun findAsk(all: List<String>): Pair<Ask, Int>? {
        val unboxed = all.map { unbox(it) }
        val lastOption = unboxed.indices.lastOrNull { option.matches(unboxed[it]) } ?: return null
        var first = lastOption
        while (first > 0 && option.matches(unboxed[first - 1])) first--
        if (lastOption == first) return null
        val questionAt = (first - 1 downTo 0).firstOrNull { unboxed[it].isNotBlank() && !onlyBox.matches(unboxed[it]) } ?: return null
        if (!unboxed[questionAt].endsWith("?")) return null
        // The request's box: a rule or a box top starting at the left edge, below the last message
        // (a numbered list in a reply is no request).
        val lastMessage = all.indices.lastOrNull { said.matches(all[it]) || you.matches(all[it]) } ?: -1
        val top = (questionAt - 1 downTo lastMessage + 1).firstOrNull { all[it].isNotEmpty() && all[it][0] in "─━╭" && onlyBox.matches(all[it]) } ?: return null
        val body = (top + 1 until questionAt).map { unboxed[it] }.filter { it.isNotBlank() && !onlyBox.matches(it) }
        val options = (first..lastOption).map { i ->
            val m = option.find(unboxed[i])!!
            val label = m.groupValues[2].removeSuffix("(esc)").trim()
            Option(m.groupValues[1], label, choiceOf(label))
        }
        val ask = Ask(body.firstOrNull().orEmpty(), body.drop(1).joinToString("\n"), unboxed[questionAt], options)
        return ask to top
    }

    private fun unbox(line: String) = line.trim().trimStart('│', ' ').trimEnd('│', ' ').trim()

    private fun choiceOf(label: String): Choice {
        val l = label.lowercase()
        return when {
            l.startsWith("yes") && listOf("don't ask", "always", "allow all", "accept edits", "auto-approve", "this session").any { it in l } -> Choice.ALWAYS
            l.startsWith("yes") -> Choice.YES
            l.startsWith("no") -> Choice.NO
            else -> Choice.OTHER
        }
    }
}
