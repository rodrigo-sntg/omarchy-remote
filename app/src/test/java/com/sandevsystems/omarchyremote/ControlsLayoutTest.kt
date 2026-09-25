package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.ControlsLayout
import com.sandevsystems.omarchyremote.display.ControlsLayout.Idle
import com.sandevsystems.omarchyremote.display.ControlsLayout.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlsLayoutTest {
    private val viewW = 2000f
    private val viewH = 900f

    @Test
    fun aGroupDroppedNearAnEdgeSnapsToIt() {
        // 200x400 group dropped with its left at 1760: 40 px from the right edge -> glued at the margin.
        val (x, y) = ControlsLayout.snap(1760f, 300f, 200f, 400f, viewW, viewH, margin = 24f, pull = 80f)
        assertEquals(viewW - 200f - 24f, x, 0.1f)
        assertEquals(300f, y, 0.1f)
    }

    @Test
    fun aGroupStaysWhereDroppedInTheMiddleButNeverOffScreen() {
        assertEquals(800f to 300f, ControlsLayout.snap(800f, 300f, 200f, 100f, viewW, viewH, 24f, 80f))
        assertEquals(24f to 24f, ControlsLayout.snap(-500f, -90f, 200f, 100f, viewW, viewH, 24f, 80f))
    }

    @Test
    fun onTheSidesItIsAColumnOnTopOrBottomARow() {
        assertTrue(ControlsLayout.vertical(0.95f, 0.5f))
        assertTrue(ControlsLayout.vertical(0.03f, 0.8f))
        assertFalse(ControlsLayout.vertical(0.5f, 0.05f))
        assertFalse(ControlsLayout.vertical(0.5f, 0.95f))
    }

    @Test
    fun theChoicesAreSavedAndReadBack() {
        val layout = ControlsLayout(setOf(Tool.RIGHT, Tool.TEXT), ControlsLayout.Size.LARGE, Idle.HIDE, 0.1f to 0.9f, 0.9f to 0.2f)
        assertEquals(layout, ControlsLayout.decode(layout.encode()))
        assertEquals(ControlsLayout.DEFAULT, ControlsLayout.decode(null))
        assertEquals(ControlsLayout.DEFAULT, ControlsLayout.decode("lixo"))
    }

    @Test
    fun theDefaultsAreTheThumbLayout() {
        val d = ControlsLayout.DEFAULT
        assertEquals(setOf(Tool.RIGHT, Tool.HOLD, Tool.LOUPE, Tool.COPY, Tool.PASTE), d.tools)
        assertTrue(d.toolsAt.first > 0.9f && d.toolsAt.second > 0.6f)  // right thumb
        assertTrue(d.typeAt.first < 0.1f && d.typeAt.second > 0.8f)    // left thumb
    }

    @Test
    fun aLayoutSavedBeforeCopyAndPasteGetsThemOnce() {
        val old = "RIGHT,LOUPE|MEDIUM|FADE|0.9:0.7|0.05:0.88"
        assertEquals(setOf(Tool.RIGHT, Tool.LOUPE, Tool.COPY, Tool.PASTE), ControlsLayout.decode(old).tools)
        // Taken out afterwards, they stay out.
        val chosen = ControlsLayout.decode(old).copy(tools = setOf(Tool.RIGHT))
        assertEquals(setOf(Tool.RIGHT), ControlsLayout.decode(chosen.encode()).tools)
    }
}
