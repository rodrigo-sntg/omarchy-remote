package com.sandevsystems.omarchyremote.display

import org.json.JSONObject

/**
 * Where decoded-to-be video goes: the attached decoder, or held from the last keyframe until one is
 * attached. Thread safe: frames and messages come from the socket thread, attach from the main thread.
 */
class FrameRouter {
    private val lock = Any()
    private var sink: ((ByteArray) -> Unit)? = null
    private val pending = mutableListOf<ByteArray>()

    fun attach(sink: (ByteArray) -> Unit) = synchronized(lock) {
        this.sink = sink
        pending.forEach(sink)
        pending.clear()
    }

    fun detach() = synchronized(lock) { sink = null }

    /** A new stream ("screen" or "display": another monitor, size or quality) starts with its own
     * keyframe right after this message; it must wait for the decoder the main thread will make. */
    fun onText(text: String) {
        val type = runCatching { JSONObject(text).optString("type") }.getOrDefault("")
        if (type == "screen" || type == "display") detach()
    }

    fun deliver(frames: List<ByteArray>) = synchronized(lock) {
        if (frames.isEmpty()) return@synchronized
        val sink = sink
        if (sink != null) {
            frames.forEach(sink)
            return@synchronized
        }
        pending += frames
        // Before a decoder exists keep only what is needed to start: from the last keyframe.
        val lastKeyframe = pending.indexOfLast { AnnexBSplitter.nalType(it) == 7 }
        if (lastKeyframe > 0) pending.subList(0, lastKeyframe).clear()
    }
}
