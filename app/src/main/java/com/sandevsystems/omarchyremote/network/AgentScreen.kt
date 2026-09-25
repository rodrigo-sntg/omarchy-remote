package com.sandevsystems.omarchyremote.network

import com.sandevsystems.omarchyremote.ui.tr

/**
 * The agent's screen for the phone: Claude Code (and the like) end their screen with chrome — the
 * input box, shortcut hints, an update notice, the status line — that pushes the actual message out
 * of a small preview. Only that trailing stretch is taken off (the agent's own text is never
 * touched), and what matters in it becomes a one-line footer.
 */
object AgentScreen {
    data class Screen(val body: String, val footer: String?)

    private val border = Regex("^[\\s╭╮╰╯│─━┌┐└┘├┤┬┴┼═║>›❯|.·\\u2800-\\u28FF]*$")
    private val boxedPrompt = Regex("^\\s*│.*│?\\s*$")
    private val hints = listOf("? for shortcuts", "shift+tab", "esc to interrupt", "Update available", "⏵⏵", "auto-accept", "bypass permissions")
    private val context = Regex("ctx (\\d+)%")
    private val statusLine = Regex("\\bon \\S+ \\|| \\| .*ctx \\d+%")
    private val spinnerMark = "[*✻✶✳✢·✽⏺]"
    private val worked = Regex("^\\s*$spinnerMark\\s+\\w+ for ((?:\\d+h )?(?:\\d+m )?\\d+s)\\b(.*)$")
    private val working = Regex("^\\s*(?:$spinnerMark\\s+\\S+…|[•◦]?\\s*Working)\\s*\\((\\d+[hms][^·•)]*?)\\s*[·•)]")
    private val shells = Regex("(\\d+) shells? still running")

    /** Codex's input box: its prompt mark at the line start; the model line and sparkles follow. */
    private val codexInput = Regex("^›[\\s\\u2800-\\u28FF]")

    /** Claude Code's input box starts with a full-width rule; everything under it is chrome. */
    private val rule = Regex("^\\s*[─━]{10,}\\s*$")

    fun clean(raw: String): Screen {
        val all = raw.lines().map { it.trimEnd() }
        // Cut at the input box's top rule, if it is near the bottom (team lines and the status line
        // can follow it); what matters of what was cut, the context use, is kept for the footer.
        val boxTop = all.indices.drop(maxOf(0, all.size - 25)).firstOrNull { rule.matches(all[it]) }
            ?: all.indices.drop(maxOf(0, all.size - 15)).lastOrNull { codexInput.containsMatchIn(all[it]) }
        val lines = if (boxTop != null) all.subList(0, boxTop) else all
        var end = lines.size
        val footer = mutableListOf<String>()
        var contextUse: String? = boxTop?.let { all.subList(it, all.size).firstNotNullOfOrNull { l -> context.find(l)?.groupValues?.get(1) } }
        while (end > 0) {
            val line = lines[end - 1]
            val chrome = when {
                line.isBlank() || border.matches(line) || boxedPrompt.matches(line) -> true
                working.containsMatchIn(line) -> {
                    footer.add(0, tr("Trabalhando… ${working.find(line)!!.groupValues[1].trim()}", "Working… ${working.find(line)!!.groupValues[1].trim()}"))
                    true
                }
                hints.any { it in line } -> true
                context.containsMatchIn(line) || statusLine.containsMatchIn(line) -> {
                    contextUse = context.find(line)?.groupValues?.get(1) ?: contextUse
                    true
                }
                worked.matches(line) -> {
                    val m = worked.find(line)!!
                    val shellCount = shells.find(m.groupValues[2])?.groupValues?.get(1)?.toInt()
                    footer.add(0, listOfNotNull(tr("Trabalhou ${m.groupValues[1]}", "Worked ${m.groupValues[1]}"),
                        shellCount?.let { if (it == 1) tr("1 shell rodando", "1 shell running") else tr("$it shells rodando", "$it shells running") }).joinToString(" · "))
                    true
                }
                else -> false
            }
            if (!chrome) break
            end--
        }
        contextUse?.let { footer.add(tr("contexto $it%", "context $it%")) }
        val body = lines.subList(0, end).dropWhile { it.isBlank() }.joinToString("\n")
        return Screen(body, footer.joinToString(" · ").ifEmpty { null })
    }
}
