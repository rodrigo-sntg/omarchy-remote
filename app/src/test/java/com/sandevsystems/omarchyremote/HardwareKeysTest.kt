package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.HardwareKeys
import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.ModifierKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Android KeyEvent codes and meta bits, as plain numbers (android.view.KeyEvent is a stub in JVM tests). */
class HardwareKeysTest {
    @Test
    fun lettersDigitsAndSymbolsKeepTheirPlace() {
        assertEquals(KeyStroke(0x04), HardwareKeys.stroke(29, 0))        // A
        assertEquals(KeyStroke(0x1D), HardwareKeys.stroke(54, 0))        // Z
        assertEquals(KeyStroke(0x1E), HardwareKeys.stroke(8, 0))         // 1
        assertEquals(KeyStroke(0x27), HardwareKeys.stroke(7, 0))         // 0
        assertEquals(KeyStroke(0x38), HardwareKeys.stroke(76, 0))        // /
    }

    @Test
    fun editingNavigationAndFunctionKeys() {
        assertEquals(KeyStroke(0x28), HardwareKeys.stroke(66, 0))        // Enter
        assertEquals(KeyStroke(0x29), HardwareKeys.stroke(111, 0))       // Esc
        assertEquals(KeyStroke(0x2A), HardwareKeys.stroke(67, 0))        // Backspace
        assertEquals(KeyStroke(0x4C), HardwareKeys.stroke(112, 0))       // Delete
        assertEquals(KeyStroke(0x52), HardwareKeys.stroke(19, 0))        // Up
        assertEquals(KeyStroke(0x3A), HardwareKeys.stroke(131, 0))       // F1
        assertEquals(KeyStroke(0x45), HardwareKeys.stroke(142, 0))       // F12
    }

    @Test
    fun modifiersComeFromTheMetaState() {
        val ctrlShift = 0x1000 or 0x1                                    // META_CTRL_ON | META_SHIFT_ON
        assertEquals(KeyStroke(0x06, ModifierKeys.CTRL or ModifierKeys.SHIFT), HardwareKeys.stroke(31, ctrlShift)) // Ctrl+Shift+C
        assertEquals(KeyStroke(0x2C, ModifierKeys.SUPER), HardwareKeys.stroke(62, 0x10000))                          // Super+Space
        assertEquals(KeyStroke(0x2B, ModifierKeys.ALT), HardwareKeys.stroke(61, 0x2))                                  // Alt+Tab
    }

    @Test
    fun modifierKeysAloneAndUnknownKeysAreNotSent() {
        assertNull(HardwareKeys.stroke(113, 0x1000))  // Ctrl pressed alone: waits for the key it modifies
        assertNull(HardwareKeys.stroke(3, 0))         // Home button of the phone
    }
}
