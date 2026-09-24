package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.RemoteAction
import com.sandevsystems.omarchyremote.display.RemoteAction.Drag
import com.sandevsystems.omarchyremote.display.RemoteAction.Hold
import com.sandevsystems.omarchyremote.display.RemoteAction.Fling
import com.sandevsystems.omarchyremote.display.RemoteAction.Pan
import com.sandevsystems.omarchyremote.display.RemoteAction.Press
import com.sandevsystems.omarchyremote.display.RemoteAction.Release
import com.sandevsystems.omarchyremote.display.RemoteAction.ResetZoom
import com.sandevsystems.omarchyremote.display.RemoteAction.RightClick
import com.sandevsystems.omarchyremote.display.RemoteAction.Scroll
import com.sandevsystems.omarchyremote.display.RemoteAction.Tap
import com.sandevsystems.omarchyremote.display.RemoteAction.Zoom
import com.sandevsystems.omarchyremote.display.RemoteGesture
import com.sandevsystems.omarchyremote.display.ScrollFling
import com.sandevsystems.omarchyremote.display.ViewTransform
import com.sandevsystems.omarchyremote.input.TouchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewTransformTest {
    // 2340x980 video in a 2340x1080 view: fitted full width, 50 px bars top and bottom.
    private fun transform() = ViewTransform(2340f, 1080f, 2340, 980)

    @Test
    fun fittedVideoMapsTouchesAndIgnoresBars() {
        val t = transform()
        assertEquals(0.5f to 0.5f, t.toContent(1170f, 540f))
        assertEquals(0f to 0f, t.toContent(0f, 50f))
        assertNull(t.toContent(100f, 20f))
    }

    @Test
    fun zoomKeepsTheFocusPointStill() {
        val t = transform()
        val before = t.toContent(585f, 295f)!!
        t.zoomBy(2f, 585f, 295f)
        val after = t.toContent(585f, 295f)!!
        assertEquals(before.first, after.first, 0.001f)
        assertEquals(before.second, after.second, 0.001f)
        assertEquals(2f, t.zoom, 0.001f)
    }

    @Test
    fun zoomIsLimitedAndPanStaysInsideTheVideo() {
        val t = transform()
        t.zoomBy(10f, 1170f, 540f)
        assertEquals(ViewTransform.MAX_ZOOM, t.zoom, 0.001f)
        t.panBy(100_000f, 100_000f) // as far as possible toward the top-left corner
        assertEquals(0f to 0f, t.toContent(0f, 0f))
        t.zoomBy(0.01f, 0f, 0f)
        assertEquals(1f, t.zoom, 0.001f)
        assertEquals(0.5f to 0.5f, t.toContent(1170f, 540f)) // back to the fitted position
    }

    @Test
    fun monitorPointMapsBackToTheView() {
        val t = transform()
        assertEquals(1170f to 540f, t.toView(0.5f, 0.5f))
        t.zoomBy(2f, 1170f, 540f)
        assertEquals(1170f to 540f, t.toView(0.5f, 0.5f))
    }

    @Test
    fun zoomedViewFollowsTheCursorNearTheEdge() {
        val t = transform()
        t.zoomBy(2f, 1170f, 540f) // shows the middle half of the monitor
        assertTrue(t.follow(0.5f, 0.5f, margin = 100f).not()) // already comfortably visible
        assertTrue(t.follow(0.95f, 0.5f, margin = 100f)) // near the right edge of the monitor: pans
        val (x, _) = t.toView(0.95f, 0.5f)
        assertTrue(x <= 2340f - 100f + 0.5f)
    }

    @Test
    fun matrixPlacesTheStretchedTextureOnTheVideoRect() {
        val t = transform()
        val (sx, sy, tx, ty) = t.matrixValues()
        assertEquals(1f, sx, 0.001f)
        assertEquals(980f / 1080f, sy, 0.001f)
        assertEquals(0f, tx, 0.001f)
        assertEquals(50f, ty, 0.001f)
    }
}

class RemoteGestureTest {
    private val gesture = RemoteGesture(touchSlop = 20f, longPressMs = 500, scrollStepPx = 50f)
    private val actions = mutableListOf<RemoteAction>()
    private fun p(id: Long, x: Float, y: Float) = TouchPoint(id, x, y)
    private fun frame(t: Long, vararg points: TouchPoint) { actions += gesture.onFrame(points.toList(), t) }

    @Test
    fun tapClicksWhereTheFingerLanded() {
        frame(0, p(1, 100f, 100f))
        frame(80, p(1, 105f, 102f))
        frame(120)
        assertEquals(listOf<RemoteAction>(Tap(100f, 100f)), actions)
    }

    @Test
    fun slidingOneFingerScrollsLikeAPhone() {
        frame(0, p(1, 100f, 500f))
        frame(16, p(1, 102f, 440f)) // -60 px: the page follows the finger
        frame(32, p(1, 104f, 390f)) // -110 px total
        frame(400)                  // slow lift: no inertia
        assertEquals(-2, actions.filterIsInstance<Scroll>().sumOf { it.vertical })
        assertEquals(0, actions.filterIsInstance<Scroll>().sumOf { it.horizontal })
        assertTrue(actions.all { it is Scroll })
    }

    @Test
    fun aMostlySidewaysSlideScrollsSideways() {
        frame(0, p(1, 500f, 100f))
        frame(16, p(1, 400f, 110f))
        frame(32, p(1, 380f, 125f))
        frame(400)
        assertEquals(-2, actions.filterIsInstance<Scroll>().sumOf { it.horizontal })
        assertEquals(0, actions.filterIsInstance<Scroll>().sumOf { it.vertical })
    }

    @Test
    fun aQuickFlickKeepsScrolling() {
        frame(0, p(1, 100f, 800f))
        frame(16, p(1, 100f, 700f))
        frame(32, p(1, 100f, 600f)) // 100 px in 16 ms
        frame(40)
        val fling = actions.filterIsInstance<Fling>().single()
        assertTrue(fling.vy < -1f && fling.vx == 0f)
    }

    @Test
    fun holdThenSlideDragsWithTheButtonDown() {
        frame(0, p(1, 100f, 100f))
        actions += gesture.onTimeout(500)
        frame(600, p(1, 150f, 100f))
        frame(616, p(1, 200f, 120f))
        frame(632)
        assertEquals(
            listOf(Hold(100f, 100f), Press(100f, 100f), Drag(150f, 100f), Drag(200f, 120f), Release(200f, 120f)),
            actions,
        )
    }

    @Test
    fun holdAndLiftWithoutMovingIsRightClick() {
        frame(0, p(1, 300f, 300f))
        assertEquals(500L, gesture.deadline())
        actions += gesture.onTimeout(500)
        frame(800)
        assertEquals(listOf(Hold(300f, 300f), RightClick(300f, 300f)), actions)
    }

    @Test
    fun twoFingersMovingTogetherMoveTheZoomedView() {
        frame(0, p(1, 100f, 500f), p(2, 300f, 500f))
        frame(16, p(1, 100f, 440f), p(2, 300f, 440f))
        frame(32, p(1, 100f, 390f), p(2, 300f, 390f))
        frame(48)
        assertEquals(-110f, actions.filterIsInstance<Pan>().sumOf { it.dy.toDouble() }.toFloat(), 0.01f)
        assertTrue(actions.none { it is Scroll || it is Tap || it is Press })
    }

    @Test
    fun scrollFlingSlowsDownAndStops() {
        val fling = ScrollFling(0f, -3f, stepPx = 50f)  // 3 px/ms upwards
        var steps = 0
        var frames = 0
        while (!fling.done && frames < 500) {
            steps += fling.next(16).first
            frames++
        }
        assertTrue(fling.done && frames < 200)
        assertTrue(steps < -5)  // several more steps in the flick's direction
    }

    @Test
    fun pinchZoomsAndPansTheViewOnly() {
        frame(0, p(1, 400f, 500f), p(2, 600f, 500f))
        frame(16, p(1, 300f, 500f), p(2, 700f, 500f)) // distance 200 -> 400
        frame(32, p(1, 350f, 500f), p(2, 750f, 500f)) // same distance, moved right
        frame(48)
        val zoom = actions.filterIsInstance<Zoom>().fold(1f) { acc, z -> acc * z.factor }
        assertEquals(2f, zoom, 0.01f)
        assertEquals(50f, actions.filterIsInstance<Pan>().sumOf { it.dx.toDouble() }.toFloat(), 0.01f)
        assertTrue(actions.none { it is Press || it is Tap || it is Scroll })
    }

    @Test
    fun twoFingerTapShowsTheWholeMonitor() {
        frame(0, p(1, 400f, 500f))
        frame(20, p(1, 400f, 500f), p(2, 600f, 500f))
        frame(120, p(2, 600f, 500f))
        frame(140)
        assertEquals(listOf<RemoteAction>(ResetZoom), actions)
    }
}
