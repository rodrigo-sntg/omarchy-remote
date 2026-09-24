package com.sandevsystems.omarchyremote.input

/**
 * Palm rejection for the S Pen on Ver PC: the hand rests on the screen while drawing, and those
 * touches must not move or click the PC's cursor. Fingers are ignored while the pen is near and for
 * [GRACE_MS] after it leaves.
 */
class PalmGuard {
    private var near = false
    private var leftAt = Long.MIN_VALUE / 2

    fun pen(state: String, nowMs: Long) {
        near = state != "out"
        if (!near) leftAt = nowMs
    }

    fun blocksFingers(nowMs: Long): Boolean = near || nowMs - leftAt < GRACE_MS

    companion object {
        const val GRACE_MS = 800L
    }
}
