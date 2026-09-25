package com.sandevsystems.omarchyremote.network

/**
 * The agent's "Trabalhando… 10m 17s" as a live clock: the screen is read every few seconds, and in
 * between the phone counts on by itself, so the time moves each second like on the PC.
 */
object WorkingTime {
    private val clock = Regex("""(?:(\d+)h\s*)?(?:(\d+)m\s*)?(\d+)s\b""")

    fun isWorking(status: String?): Boolean = status != null && (status.startsWith("Trabalhando") || status.startsWith("Working"))

    /** [status] with its first clock moved on by [seconds]. */
    fun tick(status: String, seconds: Long): String {
        val m = clock.find(status) ?: return status
        val (h, min, s) = m.destructured
        val total = (h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + s.toLong() + seconds
        val text = when {
            total >= 3600 -> "${total / 3600}h ${total % 3600 / 60}m ${total % 60}s"
            total >= 60 -> "${total / 60}m ${total % 60}s"
            else -> "${total}s"
        }
        return status.replaceRange(m.range, text)
    }
}
