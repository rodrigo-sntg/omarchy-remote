package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.VolumeKeys
import com.sandevsystems.omarchyremote.input.VolumeKeys.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Key codes as numbers: android.view.KeyEvent is a stub in JVM tests (24/25 volume up/down, 29 A). */
class VolumeKeysTest {
    @Test
    fun volumeButtonsMoveThePcVolumeWhileConnected() {
        assertEquals(Target.VolumeUp, VolumeKeys.target(24, connected = true, enabled = true, presenting = false))
        assertEquals(Target.VolumeDown, VolumeKeys.target(25, connected = true, enabled = true, presenting = false))
    }

    @Test
    fun inPresentationModeTheyChangeSlides() {
        assertEquals(Target.NextSlide, VolumeKeys.target(25, connected = true, enabled = true, presenting = true))
        assertEquals(Target.PreviousSlide, VolumeKeys.target(24, connected = true, enabled = true, presenting = true))
    }

    @Test
    fun otherwiseThePhoneKeepsItsOwnVolume() {
        assertNull(VolumeKeys.target(24, connected = false, enabled = true, presenting = false))
        assertNull(VolumeKeys.target(24, connected = true, enabled = false, presenting = false))
        assertNull(VolumeKeys.target(29, connected = true, enabled = true, presenting = false))
        // Presentation mode is its own choice: it works even with the volume option off.
        assertEquals(Target.NextSlide, VolumeKeys.target(25, connected = true, enabled = false, presenting = true))
    }
}
