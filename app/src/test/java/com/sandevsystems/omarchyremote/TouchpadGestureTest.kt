package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.TouchPoint
import com.sandevsystems.omarchyremote.input.TouchpadAction
import com.sandevsystems.omarchyremote.input.TouchpadAction.Click
import com.sandevsystems.omarchyremote.input.TouchpadAction.DragEnd
import com.sandevsystems.omarchyremote.input.TouchpadAction.DragStart
import com.sandevsystems.omarchyremote.input.TouchpadAction.Move
import com.sandevsystems.omarchyremote.input.TouchpadAction.Scroll
import com.sandevsystems.omarchyremote.input.TouchpadAction.WorkspaceStep
import com.sandevsystems.omarchyremote.input.TouchpadAction.Zoom
import com.sandevsystems.omarchyremote.input.TouchpadGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadGestureTest {
    private fun gesture(pinch: Boolean = true, natural: Boolean = true) = TouchpadGesture(
        touchSlop = 10f, tapTimeoutMs = 250, doubleTapMs = 300, scrollStep = 50f, swipeDistance = 100f,
        pinchEnabled = pinch, naturalScroll = natural,
    )

    private val g = gesture()
    private val actions = mutableListOf<TouchpadAction>()
    private fun p(id: Long, x: Float, y: Float) = TouchPoint(id, x, y)
    private fun frame(t: Long, vararg points: TouchPoint, on: TouchpadGesture = g) { actions += on.onFrame(points.toList(), t) }

    @Test
    fun slidingMovesWithoutClicking() {
        frame(0, p(1, 0f, 0f))
        frame(16, p(1, 30f, 0f))
        frame(32, p(1, 40f, -5f))
        frame(48)
        assertEquals(40f, actions.filterIsInstance<Move>().sumOf { it.dx.toDouble() }.toFloat(), 0.01f)
        assertEquals(-5f, actions.filterIsInstance<Move>().sumOf { it.dy.toDouble() }.toFloat(), 0.01f)
        assertTrue(actions.none { it is Click })
    }

    @Test
    fun shortTapClicksAndLongStillTouchDoesNot() {
        frame(0, p(1, 0f, 0f)); frame(100)
        frame(1000, p(1, 0f, 0f)); frame(1600)
        assertEquals(listOf<TouchpadAction>(Click(1)), actions)
    }

    @Test
    fun doubleTapThenHoldDragsWithTheButtonDown() {
        frame(0, p(1, 0f, 0f)); frame(80)
        frame(200, p(2, 0f, 0f))
        frame(216, p(2, 50f, 0f))
        frame(232, p(2, 80f, 10f))
        frame(248)
        assertEquals(Click(1), actions.first())
        assertEquals(DragStart, actions[1])
        assertEquals(DragEnd, actions.last())
        assertEquals(80f, actions.filterIsInstance<Move>().sumOf { it.dx.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun doubleTapIsTwoClicks() {
        frame(0, p(1, 0f, 0f)); frame(80)
        frame(200, p(2, 0f, 0f)); frame(260)
        assertEquals(listOf<TouchpadAction>(Click(1), Click(1)), actions)
    }

    @Test
    fun twoFingerTapIsRightClickThreeFingerTapIsMiddle() {
        frame(0, p(1, 0f, 0f)); frame(20, p(1, 0f, 0f), p(2, 100f, 0f)); frame(120, p(2, 100f, 0f)); frame(140)
        frame(1000, p(1, 0f, 0f), p(2, 50f, 0f)); frame(1010, p(1, 0f, 0f), p(2, 50f, 0f), p(3, 100f, 0f)); frame(1100)
        assertEquals(listOf<TouchpadAction>(Click(2), Click(3)), actions)
    }

    @Test
    fun twoFingersScrollBothAxesInWholeSteps() {
        frame(0, p(1, 0f, 500f), p(2, 200f, 500f))
        frame(16, p(1, 0f, 440f), p(2, 200f, 440f))
        frame(32, p(1, 0f, 390f), p(2, 200f, 390f)) // up 110 px
        frame(48, p(1, 60f, 390f), p(2, 260f, 390f)) // right 60 px
        frame(64)
        val scrolls = actions.filterIsInstance<Scroll>()
        assertEquals(-2, scrolls.sumOf { it.vertical }) // natural: fingers up scroll the page down
        assertEquals(-1, scrolls.sumOf { it.horizontal }) // natural: fingers right move the view left
        assertTrue(actions.none { it is Click || it is Move })
    }

    @Test
    fun traditionalScrollInvertsTheDirection() {
        val classic = gesture(natural = false)
        frame(0, p(1, 0f, 500f), p(2, 200f, 500f), on = classic)
        frame(16, p(1, 0f, 390f), p(2, 200f, 390f), on = classic)
        frame(32, on = classic)
        assertEquals(2, actions.filterIsInstance<Scroll>().sumOf { it.vertical })
    }

    @Test
    fun threeFingerSwipeStepsOneWorkspace() {
        frame(0, p(1, 0f, 0f), p(2, 50f, 0f), p(3, 100f, 0f))
        frame(16, p(1, 80f, 0f), p(2, 130f, 0f), p(3, 180f, 0f))
        frame(32, p(1, 300f, 0f), p(2, 350f, 0f), p(3, 400f, 0f))
        frame(48)
        assertEquals(listOf<TouchpadAction>(WorkspaceStep(1)), actions)
    }

    @Test
    fun pinchZoomsAndDoesNotScroll() {
        frame(0, p(1, 400f, 500f), p(2, 600f, 500f))
        frame(16, p(1, 300f, 500f), p(2, 700f, 500f))
        frame(32)
        val zoom = actions.filterIsInstance<Zoom>().fold(1f) { acc, z -> acc * z.factor }
        assertEquals(2f, zoom, 0.01f)
        assertTrue(actions.none { it is Scroll || it is Click })
    }

    @Test
    fun withoutPinchSpreadingFingersJustScrolls() {
        val noPinch = gesture(pinch = false)
        frame(0, p(1, 400f, 500f), p(2, 600f, 500f), on = noPinch)
        frame(16, p(1, 300f, 400f), p(2, 700f, 400f), on = noPinch)
        frame(32, on = noPinch)
        assertTrue(actions.none { it is Zoom })
        assertEquals(-2, actions.filterIsInstance<Scroll>().sumOf { it.vertical })
    }
}
