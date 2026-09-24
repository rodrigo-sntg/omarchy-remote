package com.sandevsystems.omarchyremote.display

import com.sandevsystems.omarchyremote.input.TouchPoint
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min

/**
 * Where the PC monitor's video sits on the phone: fitted, then zoomed/panned locally (the PC never
 * changes). The displayed rect is (left, top, width, height) in view pixels.
 */
class ViewTransform(private val viewW: Float, private val viewH: Float, videoW: Int, videoH: Int) {
    private val baseW: Float
    private val baseH: Float
    var zoom = 1f
        private set
    private var left = 0f
    private var top = 0f

    init {
        val fit = min(viewW / videoW, viewH / videoH)
        baseW = videoW * fit
        baseH = videoH * fit
        clamp()
    }

    private val width get() = baseW * zoom
    private val height get() = baseH * zoom

    /** Scales around the focus point, which stays under the fingers. */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val next = (zoom * factor).coerceIn(1f, MAX_ZOOM)
        val applied = next / zoom
        left = focusX - (focusX - left) * applied
        top = focusY - (focusY - top) * applied
        zoom = next
        clamp()
    }

    /** Zoom to [zoom] with the monitor point (nx, ny) at view point (atX, atY), as close as the edges allow
     * (e.g. the cursor above the keyboard while typing). */
    fun focusOn(nx: Float, ny: Float, zoom: Float, atX: Float, atY: Float) {
        this.zoom = zoom.coerceIn(1f, MAX_ZOOM)
        left = atX - nx * width
        top = atY - ny * height
        clamp()
    }

    fun panBy(dx: Float, dy: Float) {
        left += dx
        top += dy
        clamp()
    }

    fun reset() {
        zoom = 1f
        clamp()
    }

    /** View point -> normalized point on the monitor, or null outside the video. */
    fun toContent(x: Float, y: Float): Pair<Float, Float>? {
        val nx = (x - left) / width
        val ny = (y - top) / height
        return if (nx in 0f..1f && ny in 0f..1f) nx to ny else null
    }

    /** Normalized monitor point -> view point (e.g. where to draw the cursor ring). */
    fun toView(nx: Float, ny: Float) = (left + nx * width) to (top + ny * height)

    /** Width of the whole monitor on screen, in view pixels (finger deltas / this = monitor fraction). */
    val displayedWidth get() = width
    val displayedHeight get() = height

    /**
     * When zoomed, pans so the monitor point stays at least [margin] px inside the view.
     * Returns whether the view moved.
     */
    fun follow(nx: Float, ny: Float, margin: Float): Boolean {
        val (x, y) = toView(nx, ny)
        val dx = when {
            x < margin -> margin - x
            x > viewW - margin -> viewW - margin - x
            else -> 0f
        }
        val dy = when {
            y < margin -> margin - y
            y > viewH - margin -> viewH - margin - y
            else -> 0f
        }
        if (dx == 0f && dy == 0f) return false
        val before = left to top
        panBy(dx, dy)
        return (left to top) != before
    }

    /** TextureView stretches the video over the whole view; this maps it onto the displayed rect. */
    fun matrixValues() = floatArrayOf(width / viewW, height / viewH, left, top)

    /** Smaller than the view: centered. Larger: no empty space may show at the edges. */
    private fun clamp() {
        left = if (width <= viewW) (viewW - width) / 2 else left.coerceIn(viewW - width, 0f)
        top = if (height <= viewH) (viewH - height) / 2 else top.coerceIn(viewH - height, 0f)
    }

    companion object {
        const val MAX_ZOOM = 4f
    }
}

sealed interface RemoteAction {
    data class Tap(val x: Float, val y: Float) : RemoteAction
    /** The finger was held long enough at (x, y): next slide drags, lifting is a right click
     * (UI: haptic, and the magnifier shows the point under the finger). */
    data class Hold(val x: Float, val y: Float) : RemoteAction
    data class Press(val x: Float, val y: Float) : RemoteAction
    data class Drag(val x: Float, val y: Float) : RemoteAction
    data class Release(val x: Float, val y: Float) : RemoteAction
    data class RightClick(val x: Float, val y: Float) : RemoteAction
    /** Wheel steps at (x, y); negative = the finger moved up / left. */
    data class Scroll(val vertical: Int, val horizontal: Int, val x: Float, val y: Float) : RemoteAction
    /** The finger left while scrolling fast (px/ms): the caller keeps scrolling with [ScrollFling]. */
    data class Fling(val vx: Float, val vy: Float, val x: Float, val y: Float) : RemoteAction
    data class Zoom(val factor: Float, val focusX: Float, val focusY: Float) : RemoteAction
    data class Pan(val dx: Float, val dy: Float) : RemoteAction
    data object ResetZoom : RemoteAction
}

/**
 * Gestures on the PC screen shown on the phone (direct mode), fed with the fingers down on each
 * frame (view px). Like any touch screen:
 * tap = click; one finger sliding = scroll (locked to its main axis, a flick keeps going);
 * hold = the point is shown magnified, then slide = drag with the left button, or lift = right click;
 * pinch = zoom; two fingers moving = move the zoomed view; two-finger tap = the whole monitor.
 * Long press has no event of its own: the caller waits until [deadline] and calls [onTimeout].
 */
class RemoteGesture(private val touchSlop: Float, private val longPressMs: Long, private val scrollStepPx: Float) {
    private enum class Mode { Idle, Pending, Scrolling, Held, Dragging, TwoPending, Panning, Zooming }

    private var mode = Mode.Idle
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var dragId = 0L
    private var ids = 0L to 0L
    private var startDistance = 0f
    private var startCx = 0f
    private var startCy = 0f
    private var prevDistance = 0f
    private var prevCx = 0f
    private var prevCy = 0f
    private var horizontal = false
    private var scrollRest = 0f
    // Two latest samples while scrolling, for the flick speed.
    private var prevT = 0L
    private var prevX = 0f
    private var prevY = 0f
    private var lastT = 0L

    fun deadline(): Long? = if (mode == Mode.Pending) startTime + longPressMs else null

    fun onTimeout(now: Long): List<RemoteAction> {
        val due = deadline() ?: return emptyList()
        if (now < due) return emptyList()
        mode = Mode.Held
        return listOf(RemoteAction.Hold(startX, startY))
    }

    fun onFrame(points: List<TouchPoint>, now: Long): List<RemoteAction> {
        if (points.isEmpty()) return finish(now)
        return when (mode) {
            Mode.Idle -> {
                if (points.size >= 2) startTwo(points) else startOne(points[0], now)
                emptyList()
            }
            Mode.Pending, Mode.Held -> {
                if (points.size >= 2 && mode == Mode.Pending) {
                    startTwo(points)
                    return emptyList()
                }
                val p = points.firstOrNull { it.id == dragId } ?: return emptyList()
                if (hypot(p.x - startX, p.y - startY) <= touchSlop) return emptyList()
                if (mode == Mode.Held) {
                    mode = Mode.Dragging
                    lastX = p.x
                    lastY = p.y
                    listOf(RemoteAction.Press(startX, startY), RemoteAction.Drag(p.x, p.y))
                } else {
                    mode = Mode.Scrolling
                    horizontal = abs(p.x - startX) > abs(p.y - startY)
                    scrollRest = 0f
                    lastX = startX
                    lastY = startY
                    prevT = startTime
                    lastT = startTime
                    scroll(p, now)
                }
            }
            Mode.Scrolling -> {
                val p = points.firstOrNull { it.id == dragId } ?: return emptyList()
                scroll(p, now)
            }
            Mode.Dragging -> {
                val p = points.firstOrNull { it.id == dragId } ?: return emptyList()
                lastX = p.x
                lastY = p.y
                listOf(RemoteAction.Drag(p.x, p.y))
            }
            Mode.TwoPending, Mode.Panning, Mode.Zooming -> two(points)
        }
    }

    private fun scroll(p: TouchPoint, now: Long): List<RemoteAction> {
        scrollRest += if (horizontal) p.x - lastX else p.y - lastY
        prevT = lastT
        prevX = lastX
        prevY = lastY
        lastT = now
        lastX = p.x
        lastY = p.y
        val steps = (scrollRest / scrollStepPx).toInt()
        scrollRest -= steps * scrollStepPx
        if (steps == 0) return emptyList()
        return listOf(if (horizontal) RemoteAction.Scroll(0, steps, p.x, p.y) else RemoteAction.Scroll(steps, 0, p.x, p.y))
    }

    private fun startOne(p: TouchPoint, now: Long) {
        mode = Mode.Pending
        startX = p.x
        startY = p.y
        startTime = now
        dragId = p.id
    }

    private fun startTwo(points: List<TouchPoint>) {
        mode = Mode.TwoPending
        ids = points[0].id to points[1].id
        startDistance = distance(points[0], points[1])
        startCx = (points[0].x + points[1].x) / 2
        startCy = (points[0].y + points[1].y) / 2
        prevDistance = startDistance
        prevCx = startCx
        prevCy = startCy
    }

    private fun two(points: List<TouchPoint>): List<RemoteAction> {
        val a = points.firstOrNull { it.id == ids.first } ?: return emptyList()
        val b = points.firstOrNull { it.id == ids.second } ?: return emptyList()
        val d = distance(a, b)
        val cx = (a.x + b.x) / 2
        val cy = (a.y + b.y) / 2
        if (mode == Mode.TwoPending) {
            mode = when {
                abs(d / startDistance - 1f) > PINCH_THRESHOLD -> Mode.Zooming
                hypot(cx - startCx, cy - startCy) > touchSlop -> Mode.Panning
                else -> return emptyList()
            }
        }
        val out = if (mode == Mode.Zooming) listOf(RemoteAction.Zoom(d / prevDistance, cx, cy), RemoteAction.Pan(cx - prevCx, cy - prevCy))
        else listOf(RemoteAction.Pan(cx - prevCx, cy - prevCy))
        prevDistance = d
        prevCx = cx
        prevCy = cy
        return out
    }

    private fun finish(now: Long): List<RemoteAction> {
        val out = when (mode) {
            Mode.Pending ->
                if (now - startTime >= longPressMs) listOf(RemoteAction.RightClick(startX, startY))
                else listOf(RemoteAction.Tap(startX, startY))
            Mode.Held -> listOf(RemoteAction.RightClick(startX, startY))
            Mode.Dragging -> listOf(RemoteAction.Release(lastX, lastY))
            Mode.TwoPending -> listOf(RemoteAction.ResetZoom)
            Mode.Scrolling -> fling(now)
            else -> emptyList()
        }
        mode = Mode.Idle
        return out
    }

    /** Lifted within a moment of the last move, fast enough: keep scrolling. */
    private fun fling(now: Long): List<RemoteAction> {
        val dt = (lastT - prevT).coerceAtLeast(1L).toFloat()
        if (now - lastT > FLING_LIFT_MS) return emptyList()
        val v = if (horizontal) (lastX - prevX) / dt else (lastY - prevY) / dt
        if (abs(v) < FLING_MIN_SPEED) return emptyList()
        return listOf(if (horizontal) RemoteAction.Fling(v, 0f, lastX, lastY) else RemoteAction.Fling(0f, v, lastX, lastY))
    }

    private fun distance(a: TouchPoint, b: TouchPoint) = hypot(a.x - b.x, a.y - b.y).coerceAtLeast(1f)

    private companion object {
        const val PINCH_THRESHOLD = 0.1f
        const val FLING_LIFT_MS = 60L
        const val FLING_MIN_SPEED = 0.8f  // px/ms
    }
}

/**
 * Inertia after a flick: the speed decays (a phone's scroll friction) and is turned into wheel steps,
 * [next] once per frame. Returns (vertical, horizontal) steps.
 */
class ScrollFling(private var vx: Float, private var vy: Float, private val stepPx: Float) {
    private var restX = 0f
    private var restY = 0f
    val done get() = hypot(vx, vy) < STOP_SPEED

    fun next(dtMs: Long): Pair<Int, Int> {
        if (done) return 0 to 0
        restX += vx * dtMs
        restY += vy * dtMs
        val decay = exp(-dtMs / TAU_MS)
        vx *= decay
        vy *= decay
        val sy = (restY / stepPx).toInt()
        val sx = (restX / stepPx).toInt()
        restY -= sy * stepPx
        restX -= sx * stepPx
        return sy to sx
    }

    private companion object {
        const val TAU_MS = 325f
        const val STOP_SPEED = 0.05f
    }
}

/**
 * Ver PC follows the cursor: when the PC says it went to another of its monitors, the monitor to
 * switch to; null to stay (still here, unknown monitor, or that switch is already underway).
 */
fun followMonitor(wentTo: String?, current: String?, monitors: List<String>, pending: String?): String? =
    wentTo?.takeIf { it != current && it != pending && it in monitors }
