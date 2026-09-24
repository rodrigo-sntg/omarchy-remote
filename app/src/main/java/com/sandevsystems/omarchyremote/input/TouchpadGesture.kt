package com.sandevsystems.omarchyremote.input

import kotlin.math.abs
import kotlin.math.hypot

data class TouchPoint(val id: Long, val x: Float, val y: Float)

sealed interface TouchpadAction {
    /** Relative cursor motion, in the caller's units. */
    data class Move(val dx: Float, val dy: Float) : TouchpadAction
    /** 1 left, 2 right, 3 middle. */
    data class Click(val button: Int) : TouchpadAction
    /** Double tap and hold: the left button stays down while moving, until [DragEnd]. */
    data object DragStart : TouchpadAction
    data object DragEnd : TouchpadAction
    /** Wheel steps, already in the chosen direction: vertical positive = up, horizontal positive = right. */
    data class Scroll(val vertical: Int, val horizontal: Int) : TouchpadAction
    /** Three-finger horizontal swipe: +1 next workspace, -1 previous. */
    data class WorkspaceStep(val direction: Int) : TouchpadAction
    /** Pinch: local zoom of the screen image, around the fingers; moving the pinch pans. */
    data class Zoom(val factor: Float, val focusX: Float, val focusY: Float) : TouchpadAction
    data class Pan(val dx: Float, val dy: Float) : TouchpadAction
}

/**
 * Laptop-style touchpad, fed with the fingers down on each frame (an empty list when all lift):
 *
 * - 1 finger slides: move · tap: left click · double tap and hold, then slide: drag
 * - 2 fingers: tap = right click · slide = scroll (both axes) · pinch = zoom (if enabled)
 * - 3 fingers: tap = middle click · horizontal swipe = previous/next workspace
 *
 * Fingers rarely land together, so the count may grow during the first moments of a tap. When fingers
 * are added or lifted the reference is re-anchored, so the cursor never jumps.
 */
class TouchpadGesture(
    private val touchSlop: Float,
    private val tapTimeoutMs: Long,
    private val doubleTapMs: Long,
    private val scrollStep: Float,
    private val swipeDistance: Float,
    var pinchEnabled: Boolean = true,
    var naturalScroll: Boolean = true,
) {
    private enum class Mode { Idle, One, DragCandidate, Moving, Dragging, Two, Scrolling, Pinching, Three, Ignoring }

    private var mode = Mode.Idle
    private var startTime = 0L
    private var lastTapUp = Long.MIN_VALUE / 2
    private var travel = 0f
    private var pendingX = 0f
    private var pendingY = 0f
    private var moved = false
    private var previous = emptyMap<Long, TouchPoint>()
    private var ids = emptyList<Long>()
    private var startDistance = 1f
    private var prevDistance = 1f
    private var startCx = 0f
    private var startCy = 0f
    private var prevCx = 0f
    private var prevCy = 0f
    private var restV = 0f
    private var restH = 0f

    fun onFrame(points: List<TouchPoint>, timeMs: Long): List<TouchpadAction> {
        if (points.isEmpty()) return finish(timeMs)
        val current = points.associateBy { it.id }
        if (mode == Mode.Idle) {
            startTime = timeMs
            travel = 0f
            pendingX = 0f
            pendingY = 0f
            moved = false
            begin(points, fresh = true, timeMs)
            previous = current
            return emptyList()
        }
        if (current.keys != previous.keys) {
            val added = current.size > previous.size
            val stillTapping = mode in listOf(Mode.One, Mode.DragCandidate, Mode.Two, Mode.Three) && !moved
            if (added && (stillTapping || mode == Mode.Moving)) begin(points, fresh = false, timeMs)
            previous = current
            return emptyList()
        }
        val dx = points.map { it.x - previous.getValue(it.id).x }.average().toFloat()
        val dy = points.map { it.y - previous.getValue(it.id).y }.average().toFloat()
        previous = current
        return when (mode) {
            Mode.One, Mode.DragCandidate -> {
                travel += hypot(dx, dy)
                pendingX += dx
                pendingY += dy
                if (travel <= touchSlop) return emptyList()
                moved = true
                val move = TouchpadAction.Move(pendingX, pendingY)
                if (mode == Mode.DragCandidate) {
                    mode = Mode.Dragging
                    listOf(TouchpadAction.DragStart, move)
                } else {
                    mode = Mode.Moving
                    listOf(move)
                }
            }
            Mode.Moving, Mode.Dragging -> listOf(TouchpadAction.Move(dx, dy))
            Mode.Two, Mode.Scrolling, Mode.Pinching -> two(points)
            Mode.Three -> three(points)
            else -> emptyList()
        }
    }

    private fun begin(points: List<TouchPoint>, fresh: Boolean, timeMs: Long) {
        mode = when {
            points.size >= 3 -> Mode.Three
            points.size == 2 -> Mode.Two
            fresh && timeMs - lastTapUp <= doubleTapMs -> Mode.DragCandidate
            else -> Mode.One
        }
        ids = points.map { it.id }
        val (cx, cy) = centroid(points)
        startCx = cx; startCy = cy; prevCx = cx; prevCy = cy
        startDistance = if (points.size >= 2) distance(points[0], points[1]) else 1f
        prevDistance = startDistance
        restV = 0f
        restH = 0f
    }

    private fun two(points: List<TouchPoint>): List<TouchpadAction> {
        val tracked = ids.mapNotNull { id -> points.firstOrNull { it.id == id } }
        if (tracked.size < 2) return emptyList()
        val d = distance(tracked[0], tracked[1])
        val (cx, cy) = centroid(tracked)
        if (mode == Mode.Two) {
            mode = when {
                pinchEnabled && abs(d / startDistance - 1f) > PINCH_THRESHOLD -> Mode.Pinching
                hypot(cx - startCx, cy - startCy) > touchSlop -> Mode.Scrolling
                else -> return emptyList()
            }
            moved = true
            // Keep the movement that happened while deciding.
            prevCx = startCx; prevCy = startCy; prevDistance = startDistance
        }
        val out = if (mode == Mode.Pinching) {
            listOf(TouchpadAction.Zoom(d / prevDistance, cx, cy), TouchpadAction.Pan(cx - prevCx, cy - prevCy))
        } else {
            val sign = if (naturalScroll) 1 else -1
            restV += (cy - prevCy) * sign
            restH -= (cx - prevCx) * sign
            val v = (restV / scrollStep).toInt()
            val h = (restH / scrollStep).toInt()
            restV -= v * scrollStep
            restH -= h * scrollStep
            if (v != 0 || h != 0) listOf(TouchpadAction.Scroll(v, h)) else emptyList()
        }
        prevDistance = d; prevCx = cx; prevCy = cy
        return out
    }

    private fun three(points: List<TouchPoint>): List<TouchpadAction> {
        val (cx, cy) = centroid(points)
        if (hypot(cx - startCx, cy - startCy) > touchSlop) moved = true
        val dx = cx - startCx
        if (abs(dx) < swipeDistance) return emptyList()
        mode = Mode.Ignoring
        return listOf(TouchpadAction.WorkspaceStep(if (dx > 0) 1 else -1))
    }

    private fun finish(timeMs: Long): List<TouchpadAction> {
        val short = timeMs - startTime <= tapTimeoutMs && !moved
        val out = when (mode) {
            Mode.One, Mode.DragCandidate -> if (short) listOf(TouchpadAction.Click(1)) else emptyList()
            Mode.Dragging -> listOf(TouchpadAction.DragEnd)
            Mode.Two -> if (short) listOf(TouchpadAction.Click(2)) else emptyList()
            Mode.Three -> if (short) listOf(TouchpadAction.Click(3)) else emptyList()
            else -> emptyList()
        }
        // Only a single first tap arms "double tap and hold to drag".
        lastTapUp = if (mode == Mode.One && out.isNotEmpty()) timeMs else Long.MIN_VALUE / 2
        mode = Mode.Idle
        previous = emptyMap()
        return out
    }

    private fun centroid(points: List<TouchPoint>) = points.map { it.x }.average().toFloat() to points.map { it.y }.average().toFloat()
    private fun distance(a: TouchPoint, b: TouchPoint) = hypot(a.x - b.x, a.y - b.y).coerceAtLeast(1f)

    private companion object {
        const val PINCH_THRESHOLD = 0.1f
    }
}
