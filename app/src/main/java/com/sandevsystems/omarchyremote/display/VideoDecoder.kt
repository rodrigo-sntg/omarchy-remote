package com.sandevsystems.omarchyremote.display

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Hardware H.264 decoding straight to a Surface, on its own thread. Frames are rendered as soon as
 * they are decoded; if the queue backs up, it is dropped and decoding resumes at the next keyframe,
 * so latency never accumulates.
 */
class VideoDecoder(surface: Surface, width: Int, height: Int) {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val thread = Thread(::run, "keypad-decoder")

    @Volatile private var running = true
    @Volatile private var waitingForKeyframe = true
    /** Frames rendered to the surface (read from the main thread for the readout). */
    @Volatile var rendered = 0L
        private set

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        format.setInteger(MediaFormat.KEY_PRIORITY, 0) // real time
        codec.configure(format, surface, null, 0)
        codec.start()
        thread.start()
    }

    /** One whole frame (access unit), SPS/PPS included in keyframes. */
    fun submit(frame: ByteArray) {
        if (queue.size > MAX_QUEUE) {
            Log.w(TAG, "decoder behind; dropping ${queue.size} frames until the next keyframe")
            queue.clear()
            waitingForKeyframe = true
        }
        queue.offer(frame)
    }

    fun release() {
        running = false
        thread.join(500)
    }

    private fun run() {
        val info = MediaCodec.BufferInfo()
        var timeUs = 0L
        try {
            while (running) {
                val frame = queue.poll(5, TimeUnit.MILLISECONDS)
                if (frame != null) {
                    if (waitingForKeyframe && !isKeyframe(frame)) continue
                    waitingForKeyframe = false
                    var index = -1
                    while (running && index < 0) {
                        index = codec.dequeueInputBuffer(5_000)
                        if (index < 0) render(info)
                    }
                    if (index < 0) break
                    codec.getInputBuffer(index)!!.apply { clear(); put(frame) }
                    codec.queueInputBuffer(index, 0, frame.size, timeUs, 0)
                    timeUs += 16_666
                }
                render(info)
            }
        } catch (error: Exception) {
            Log.w(TAG, "decoder stopped: $error")
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun render(info: MediaCodec.BufferInfo) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            if (index < 0) return
            codec.releaseOutputBuffer(index, true)
            rendered++
        }
    }

    /** Keyframes carry SPS (type 7) before the IDR slices. */
    private fun isKeyframe(frame: ByteArray): Boolean {
        for (i in 0 until frame.size - 3) {
            if (frame[i].toInt() == 0 && frame[i + 1].toInt() == 0 && frame[i + 2].toInt() == 1) {
                if (frame[i + 3].toInt() and 0x1f == 7) return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "KeypadVideo"
        private const val MAX_QUEUE = 30 // frames, about half a second
    }
}
