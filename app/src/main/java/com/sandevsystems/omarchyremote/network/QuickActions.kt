package com.sandevsystems.omarchyremote.network

import com.sandevsystems.omarchyremote.ui.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A one-tap action over the agent's composer (design 5C's chips), from what fits this agent now:
 * stop it while it works; between tasks continue, summarize, review the project's changes, and
 * compact when its context is filling up. [prompt] is sent as a message (or its own /command),
 * [keys] pressed; [urgent] stands out.
 */
data class QuickAction(val id: String, val label: String, val prompt: String? = null, val keys: List<String>? = null, val urgent: Boolean = false)

fun quickActions(kind: String, status: String, context: Int?, changed: Int): List<QuickAction> {
    if (kind != "claude" && kind != "codex") return emptyList()
    return when (status) {
        "working" -> listOf(QuickAction("stop", tr("Interromper", "Interrupt"), keys = listOf("esc")))
        "idle", "done" -> buildList {
            if (context != null && context >= 70) add(QuickAction("compact", tr("Compactar $context%", "Compact $context%"), prompt = "/compact", urgent = true))
            add(QuickAction("continue", tr("Continuar", "Continue"), prompt = "continue"))
            // Each reviews in its own way: Claude Code's review command, Codex's /review.
            if (changed > 0) add(QuickAction("review", tr("Revisar o diff", "Review the diff"), prompt = if (kind == "claude") "/code-review" else "/review"))
            add(QuickAction("recap", tr("Resumir", "Summarize"), prompt = "/recap"))
        }
        else -> emptyList()
    }
}

private val claudeContext = Regex("ctx (\\d+)%")
private val codexLeft = Regex("(\\d+)% context left")

/** How full the agent's context is (%), from its status line: Claude says it used, Codex what is left. */
fun contextUsed(screen: String): Int? =
    claudeContext.findAll(screen).lastOrNull()?.groupValues?.get(1)?.toInt()
        ?: codexLeft.findAll(screen).lastOrNull()?.groupValues?.get(1)?.toInt()?.let { 100 - it }

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private val day = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private const val BREAK = 20 * 60L

/** "Hoje 10:02", "Ontem 18:40", "20/09 09:05". */
fun timeLabel(ts: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochSecond(ts).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    return when (at.toLocalDate()) {
        today -> tr("Hoje ", "Today ") + at.format(clock)
        today.minusDays(1) -> tr("Ontem ", "Yesterday ") + at.format(clock)
        else -> at.format(day)
    }
}

/** A time goes above a message that follows a break (or starts a day, or starts the chat). */
fun separatorBefore(previous: Long?, ts: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    previous == null || ts - previous >= BREAK ||
        Instant.ofEpochSecond(previous).atZone(zone).toLocalDate() != Instant.ofEpochSecond(ts).atZone(zone).toLocalDate()
