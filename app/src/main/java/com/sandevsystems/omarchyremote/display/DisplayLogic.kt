package com.sandevsystems.omarchyremote.display

import kotlin.math.min
import kotlin.math.roundToInt

/** Splits an H.264 Annex B byte stream into NAL units (start code included), across arbitrary chunks. */
class AnnexBSplitter {
    private var buffer = ByteArray(0)

    fun feed(chunk: ByteArray): List<ByteArray> {
        buffer += chunk
        val starts = mutableListOf<Int>()
        var i = 0
        while (i + 2 < buffer.size) {
            if (buffer[i].toInt() == 0 && buffer[i + 1].toInt() == 0 && buffer[i + 2].toInt() == 1) {
                starts += if (i > 0 && buffer[i - 1].toInt() == 0) i - 1 else i
                i += 3
            } else {
                i++
            }
        }
        if (starts.isEmpty()) return emptyList()
        val units = starts.zipWithNext { from, to -> buffer.copyOfRange(from, to) }.toMutableList()
        buffer = buffer.copyOfRange(starts.last(), buffer.size)
        // The host's end-of-frame marker (an access unit delimiter, always 2 bytes) is known to be whole
        // without waiting for the next start code: a still screen's last frame is shown at once.
        if (isWholeDelimiter(buffer)) {
            units += buffer
            buffer = ByteArray(0)
        }
        return units
    }

    private fun isWholeDelimiter(unit: ByteArray): Boolean {
        val header = unit.indexOfFirst { it.toInt() != 0 } + 1
        return header in 3..4 && unit.size == header + 2 && unit[header].toInt() and 0x1f == 9
    }

    companion object {
        /** 5 = IDR slice, 7 = SPS, 8 = PPS. */
        fun nalType(unit: ByteArray): Int {
            val header = unit.indexOfFirst { it.toInt() != 0 } + 1
            return unit[header].toInt() and 0x1f
        }
    }
}

/**
 * Groups NAL units into access units (whole frames). Low-latency x264 splits every frame into many
 * slices; hardware decoders expect one frame per input buffer. A frame starts at SPS/PPS/SEI/AUD or
 * at a slice whose first_mb_in_slice is 0; the frame is emitted when the next one starts.
 */
class AccessUnitAssembler {
    private val current = mutableListOf<ByteArray>()
    private var hasSlice = false

    fun add(unit: ByteArray): List<ByteArray> {
        val type = AnnexBSplitter.nalType(unit)
        val slice = type == 1 || type == 5
        val startsFrame = if (slice) firstSliceOfPicture(unit) else type in 6..9
        val done = if (startsFrame && hasSlice) listOf(flush()) else emptyList()
        current += unit
        if (slice) hasSlice = true
        return done
    }

    private fun flush(): ByteArray {
        val frame = current.reduce { acc, bytes -> acc + bytes }
        current.clear()
        hasSlice = false
        return frame
    }

    private fun firstSliceOfPicture(unit: ByteArray): Boolean {
        val header = unit.indexOfFirst { it.toInt() != 0 } + 1
        return unit.size > header + 1 && unit[header + 1].toInt() and 0x80 != 0
    }
}

/** Touch on the view -> point on the fitted (letterboxed) video in [0,1], or null when on the bars. */
fun normalizeTouch(x: Float, y: Float, viewW: Float, viewH: Float, videoW: Int, videoH: Int): Pair<Float, Float>? {
    val scale = min(viewW / videoW, viewH / videoH)
    val contentW = videoW * scale
    val contentH = videoH * scale
    val nx = (x - (viewW - contentW) / 2) / contentW
    val ny = (y - (viewH - contentH) / 2) / contentH
    return if (nx in 0f..1f && ny in 0f..1f) nx to ny else null
}

/**
 * Size of the virtual monitor for this phone: its full resolution in landscape (even, for the encoder),
 * with a scale that keeps about 1560 logical pixels of width so text stays readable.
 */
fun displayRequest(widthPx: Int, heightPx: Int): Triple<Int, Int, Double> {
    val width = maxOf(widthPx, heightPx).let { it - it % 2 }
    val height = minOf(widthPx, heightPx).let { it - it % 2 }
    val scale = ((width / 1560.0) * 4).roundToInt() / 4.0
    return Triple(width, height, scale.coerceIn(1.0, 3.0))
}
