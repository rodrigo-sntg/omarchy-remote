package com.sandevsystems.omarchyremote.input

/** Converts fractional gesture deltas to whole HID counts, carrying the remainder to the next event. */
class PointerAccumulator {
    private var restX = 0f
    private var restY = 0f

    fun add(dx: Float, dy: Float): Pair<Int, Int> {
        restX += dx
        restY += dy
        val x = restX.toInt()
        val y = restY.toInt()
        restX -= x
        restY -= y
        return x to y
    }

    fun reset() {
        restX = 0f
        restY = 0f
    }
}

/** Emits one wheel step per [stepDistance] travelled, in either direction. */
class ScrollAccumulator(private val stepDistance: Float) {
    private var rest = 0f

    fun add(distance: Float): Int {
        rest += distance
        val steps = (rest / stepDistance).toInt()
        rest -= steps * stepDistance
        return steps
    }

    fun reset() {
        rest = 0f
    }
}
