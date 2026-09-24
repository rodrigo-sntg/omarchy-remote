package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.Loupe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoupeTest {
    private val viewW = 2340f
    private val viewH = 1080f
    private val lens = 400f

    @Test
    fun theLensSitsAboveTheCursorAndShowsWhatIsAroundIt() {
        val l = Loupe.layout(1000f, 700f, viewW, viewH, lens, zoom = 2f)
        assertEquals(200f, l.sourceSize)
        assertEquals(900f, l.sourceLeft)  // centered on the cursor
        assertEquals(600f, l.sourceTop)
        assertTrue(l.lensTop + lens < 600f)  // above, clear of the region it copies (or it would copy itself)
        assertEquals(1000f - lens / 2, l.lensLeft)
        assertEquals(lens / 2, l.markX)  // the cursor is in the middle of the lens
        assertEquals(lens / 2, l.markY)
    }

    @Test
    fun nearTheTopTheLensGoesBelow() {
        val l = Loupe.layout(1000f, 150f, viewW, viewH, lens, zoom = 2f)
        assertTrue(l.lensTop > 150f + l.sourceSize / 2)
    }

    @Test
    fun atAnEdgeTheLensStaysOnScreenAndTheMarkFollowsTheCursor() {
        val l = Loupe.layout(20f, 1070f, viewW, viewH, lens, zoom = 2f)
        assertEquals(0f, l.lensLeft)
        assertEquals(0f, l.sourceLeft)  // the copied region stays inside the view
        assertEquals(viewH - l.sourceSize, l.sourceTop)
        assertEquals(20f * 2, l.markX)  // cursor 20 px from the left edge, magnified 2x
        assertEquals((1070f - (viewH - 200f)) * 2, l.markY)
    }

    @Test
    fun zoomStepsCycleThroughOff() {
        assertEquals(listOf(2f, 3f, 0f, 2f), listOf(0f, 2f, 3f, 0f).map(Loupe::next))
    }
}
