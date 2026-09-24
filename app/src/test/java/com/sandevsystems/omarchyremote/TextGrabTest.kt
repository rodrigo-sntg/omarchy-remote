package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.TextGrab
import com.sandevsystems.omarchyremote.display.ViewTransform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextGrabTest {
    // A 2000x1000 monitor on a 2000x1000 view: view px = monitor px / 2000, / 1000.
    private val t = ViewTransform(2000f, 1000f, 2000, 1000)

    @Test
    fun aDraggedRectangleBecomesARegionOfTheMonitor() {
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), TextGrab.region(200f, 200f, 800f, 600f, t), 1e-4f)
        // Dragged up and to the left: same rectangle.
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), TextGrab.region(800f, 600f, 200f, 200f, t), 1e-4f)
    }

    @Test
    fun partsOutsideTheVideoAreCutAndTinyOnesIgnored() {
        assertArrayEquals(floatArrayOf(0.9f, 0f, 0.1f, 0.1f), TextGrab.region(1800f, -50f, 2300f, 100f, t), 1e-4f)
        assertNull(TextGrab.region(500f, 500f, 510f, 505f, t))  // a tap, not a selection
    }
}
