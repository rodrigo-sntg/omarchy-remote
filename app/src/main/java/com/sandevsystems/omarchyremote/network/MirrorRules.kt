package com.sandevsystems.omarchyremote.network

/** Which of the phone's notifications go to the PC (NotificationMirror), and when again. */
object MirrorRules {
    data class Candidate(
        val pkg: String, val ongoing: Boolean, val groupSummary: Boolean, val category: String?,
        val title: String, val text: String,
    )

    /** Never: our own, ongoing ones (music, downloads, services), group summaries, progress and navigation. */
    private val skippedCategories = setOf("progress", "navigation", "service", "sys", "status", "transport", "stopwatch", "location_sharing")

    fun send(c: Candidate, ownPackage: String, excluded: Set<String>): Boolean =
        c.pkg != ownPackage && !c.ongoing && !c.groupSummary && c.category !in skippedCategories &&
            c.pkg !in excluded && (c.title.isNotBlank() || c.text.isNotBlank())

    /** An app re-posts the same notification often (a timestamp, a count): only new words go again. */
    fun changed(key: String, c: Candidate, seen: MutableMap<String, Int>): Boolean {
        val hash = (c.title + "\u0000" + c.text).hashCode()
        if (seen[key] == hash) return false
        seen[key] = hash
        if (seen.size > 200) seen.keys.firstOrNull()?.let { seen.remove(it) }
        return true
    }

    fun clip(text: String, limit: Int) = if (text.length <= limit) text else text.take(limit)
}
