package com.sandevsystems.omarchyremote.display

/** "Copiar texto da tela": the rectangle dragged over Ver PC, as a region of the monitor. */
object TextGrab {
    /** Smaller than this (view px) on either side is a tap, not a selection. */
    private const val MIN_PX = 24f

    /** x, y, w, h as fractions of the monitor, cut to the video; null when too small. */
    fun region(x0: Float, y0: Float, x1: Float, y1: Float, t: ViewTransform): FloatArray? {
        if (kotlin.math.abs(x1 - x0) < MIN_PX || kotlin.math.abs(y1 - y0) < MIN_PX) return null
        val (ox, oy) = t.toView(0f, 0f)
        fun nx(x: Float) = ((x - ox) / t.displayedWidth).coerceIn(0f, 1f)
        fun ny(y: Float) = ((y - oy) / t.displayedHeight).coerceIn(0f, 1f)
        val left = nx(minOf(x0, x1))
        val top = ny(minOf(y0, y1))
        val w = nx(maxOf(x0, x1)) - left
        val h = ny(maxOf(y0, y1)) - top
        return if (w <= 0f || h <= 0f) null else floatArrayOf(left, top, w, h)
    }
}
