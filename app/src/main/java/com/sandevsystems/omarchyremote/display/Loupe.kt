package com.sandevsystems.omarchyremote.display

/**
 * The magnifier on Ver PC: a round lens near the PC's cursor showing what is around it, larger.
 * Only on the phone: it copies what the phone already shows, the PC is not involved.
 */
object Loupe {
    /** Off, then 2x, then 3x, then off again (one button). */
    val STEPS = listOf(0f, 2f, 3f)

    fun next(zoom: Float): Float = STEPS[(STEPS.indexOf(zoom).coerceAtLeast(0) + 1) % STEPS.size]

    /**
     * [sourceLeft]/[sourceTop]/[sourceSize]: the square of the view to copy (view px, inside the view).
     * [lensLeft]/[lensTop]: where the lens goes, above the cursor or below it near the top, never over
     * the square it copies. [markX]/[markY]: the cursor inside the lens.
     */
    data class Layout(
        val sourceLeft: Float, val sourceTop: Float, val sourceSize: Float,
        val lensLeft: Float, val lensTop: Float, val markX: Float, val markY: Float,
    )

    fun layout(cursorX: Float, cursorY: Float, viewW: Float, viewH: Float, lens: Float, zoom: Float): Layout {
        val size = minOf(lens / zoom, viewW, viewH)
        val sx = (cursorX - size / 2).coerceIn(0f, viewW - size)
        val sy = (cursorY - size / 2).coerceIn(0f, viewH - size)
        val gap = size / 2 + GAP
        val above = cursorY - gap - lens
        val top = if (above >= 0f) above else (cursorY + gap).coerceAtMost(viewH - lens)
        val left = (cursorX - lens / 2).coerceIn(0f, viewW - lens)
        return Layout(sx, sy, size, left, top, (cursorX - sx) * zoom, (cursorY - sy) * zoom)
    }

    private const val GAP = 24f
}
