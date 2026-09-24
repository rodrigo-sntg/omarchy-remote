package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.TermKeys
import com.sandevsystems.omarchyremote.input.TermModifiers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TermKeysTest {
    @Test
    fun namedKeysAreTheTerminalSequences() {
        assertArrayEquals(byteArrayOf(0x1b), TermKeys.bytes("esc"))
        assertArrayEquals(byteArrayOf(0x0d), TermKeys.bytes("enter"))
        assertArrayEquals("\u001b[A".toByteArray(), TermKeys.bytes("up"))
        assertArrayEquals(byteArrayOf(0x00), TermKeys.bytes("prefix")) // Ctrl+Space, herdr's prefix in Omarchy
        assertNull(TermKeys.bytes("f13"))
    }

    @Test
    fun controlCodesForLettersAndTheUsualSymbols() {
        assertArrayEquals(byteArrayOf(0x03), TermKeys.control("c"))
        assertArrayEquals(byteArrayOf(0x03), TermKeys.control("C"))
        assertArrayEquals(byteArrayOf(0x1b), TermKeys.control("["))
        assertNull(TermKeys.control("1"))
        assertNull(TermKeys.control("ab"))
    }

    @Test
    fun stickyModifiersApplyOnceThenRelease() {
        val modifiers = TermModifiers()
        modifiers.ctrl = true
        assertArrayEquals(byteArrayOf(0x03), modifiers.apply("c"))
        assertFalse(modifiers.ctrl)
        assertArrayEquals("c".toByteArray(), modifiers.apply("c"))
        modifiers.alt = true
        assertArrayEquals(byteArrayOf(0x1b, 'x'.code.toByte()), modifiers.apply("x"))
        assertFalse(modifiers.alt)
        modifiers.ctrl = true
        modifiers.alt = true
        assertArrayEquals(byteArrayOf(0x1b, 0x02), modifiers.apply("b"))
    }

    @Test
    fun ctrlWithoutControlCodeSendsTheCharacterAndReleases() {
        val modifiers = TermModifiers()
        modifiers.ctrl = true
        assertArrayEquals("1".toByteArray(), modifiers.apply("1"))
        assertFalse(modifiers.ctrl)
    }
}
