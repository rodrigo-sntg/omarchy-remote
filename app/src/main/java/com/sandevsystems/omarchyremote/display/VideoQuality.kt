package com.sandevsystems.omarchyremote.display

import com.sandevsystems.omarchyremote.ui.tr

/** Video presets for the network at hand (docs/PLANO-V2.md §6): frames per second and size vs. the phone. */
enum class VideoQuality(val fps: Int, val scale: Float, private val pt: String, private val en: String) {
    SHARP(60, 1.0f, "Nítido", "Sharp"),
    BALANCED(30, 0.75f, "Equilibrado", "Balanced"),
    LIGHT(20, 0.5f, "Leve (4G)", "Light (4G)");

    val label: String get() = tr(pt, en)

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: SHARP
    }
}

/** Round trip (smoothed) and decoded frames per second. Main thread. The chip only speaks up when
 * the link is slow — milliseconds and frame rates mean nothing to the person. */
class VideoStats {
    private val frames = ArrayDeque<Long>()
    var rttMs: Int? = null
        private set

    fun frame(nowMs: Long) {
        frames.addLast(nowMs)
        while (frames.size > 240) frames.removeFirst()
    }

    fun pong(sentMs: Long, nowMs: Long) {
        val sample = (nowMs - sentMs).toInt().coerceAtLeast(0)
        rttMs = rttMs?.let { (it * 0.75 + sample * 0.25).toInt() } ?: sample
    }

    fun fps(nowMs: Long) = frames.count { it >= nowMs - 1_000 }

    fun text(@Suppress("UNUSED_PARAMETER") nowMs: Long): String = if ((rttMs ?: 0) > SLOW_MS) tr("rede lenta", "slow network") else ""

    private companion object {
        const val SLOW_MS = 150
    }

    fun reset() {
        frames.clear()
        rttMs = null
    }
}
