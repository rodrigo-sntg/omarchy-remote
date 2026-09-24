package com.sandevsystems.omarchyremote.display

import android.view.Surface

/**
 * One video stream shown on one surface: starts the decoder once both the video size (from the host)
 * and the surface (from the UI) are known, and releases it when either goes away. Main thread only.
 */
class VideoPipe {
    var channel: VideoChannel? = null
        private set
    private var decoder: VideoDecoder? = null
    private var surface: Surface? = null
    private var size: Pair<Int, Int>? = null

    fun open(channel: VideoChannel) {
        close()
        this.channel = channel
    }

    /** A new size (another monitor or quality on the same connection) restarts the decoder on the same surface. */
    fun setSize(width: Int, height: Int) {
        if (size != null && size != width to height) stopDecoder()
        size = width to height
        // Same size: the channel held the new stream for a new decoder; the current one takes it.
        decoder?.let { decoder -> channel?.attach(decoder) }
        start()
    }

    /** Frames the decoder has put on screen so far (for the frames-per-second readout). */
    fun rendered(): Long = decoder?.rendered ?: 0L

    fun attach(surface: Surface) {
        this.surface = surface
        start()
    }

    fun detach() {
        stopDecoder()
        surface = null
    }

    /** Ends the stream but keeps the surface: switching monitor reuses the same view, which will not
     * hand its surface over again. */
    fun close() {
        channel?.close()
        stopDecoder()
        channel = null
        size = null
    }

    private fun stopDecoder() {
        channel?.detach()
        decoder?.release()
        decoder = null
    }

    private fun start() {
        val surface = surface ?: return
        val (width, height) = size ?: return
        val channel = channel ?: return
        if (decoder != null) return
        decoder = VideoDecoder(surface, width, height).also(channel::attach)
    }
}
