package com.sandevsystems.omarchyremote.network

import android.net.Uri
import com.sandevsystems.omarchyremote.ui.tr

/** A file on its way between the phone and the PC, as the screen shows it (the notification says the same). */
data class Transfer(
    val id: Int,
    val name: String,
    val incoming: Boolean,
    val done: Long,
    val total: Long?,
    val state: State,
    /** Where it landed: "~/Downloads" on the PC, "Download/Omarchy Remote" on the phone. */
    val where: String? = null,
    val uri: Uri? = null,
    val mime: String? = null,
    val error: String? = null,
    /** When it finished (elapsed ms), to let the banner go after a while. */
    val endedAt: Long? = null,
) {
    enum class State { RUNNING, DONE, FAILED }

    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }

    fun status(): String = when (state) {
        State.RUNNING -> {
            val verb = if (incoming) tr("Trazendo", "Bringing") else tr("Enviando", "Sending")
            val f = fraction
            when {
                f != null -> "$verb · ${(f * 100).toInt()}% · ${sizes(done, total!!)}"
                done > 0 -> "$verb · ${size(done)}"
                else -> "$verb…"
            }
        }
        State.DONE -> (if (incoming) tr("No celular", "On the phone") else tr("No PC", "On the PC")) + (where?.let { " · $it" } ?: "")
        State.FAILED -> tr("Falhou", "Failed") + (error?.let { ": $it" } ?: "")
    }

    companion object {
        /** The folder a PC path is in, from the home as "~". */
        fun pcFolder(path: String): String = path.substringBeforeLast('/').replace(Regex("^/home/[^/]+"), "~")

        private fun unit(bytes: Long) = when {
            bytes < 1024 -> 1L to "B"
            bytes < 1024 * 1024 -> 1024L to "KB"
            bytes < 1024L * 1024 * 1024 -> 1024L * 1024 to "MB"
            else -> 1024L * 1024 * 1024 to "GB"
        }

        private fun number(bytes: Long, div: Long) = if (div <= 1024) (bytes / div).toString() else String.format("%.1f", bytes.toDouble() / div)

        fun size(bytes: Long): String = unit(bytes).let { (div, u) -> "${number(bytes, div)} $u" }

        /** "3,2 de 7,0 MB": both in the total's unit. */
        fun sizes(done: Long, total: Long): String = unit(total).let { (div, u) -> "${number(done, div)} ${tr("de", "of")} ${number(total, div)} $u" }
    }
}
