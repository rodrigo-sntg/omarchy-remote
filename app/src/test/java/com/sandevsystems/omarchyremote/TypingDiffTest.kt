package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.ViewTransform
import com.sandevsystems.omarchyremote.input.TypingDiff
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingDiffTest {
    @Test
    fun typedLettersAreSentAsTheyCome() {
        assertEquals(TypingDiff.Change(0, "a"), TypingDiff.between("ol", "ola"))
    }

    @Test
    fun backspaceAndAutocorrectBecomeBackspacesThenTheNewText() {
        assertEquals(TypingDiff.Change(1, ""), TypingDiff.between("ola", "ol"))
        // The keyboard replaced "vc " by "você ": two backspaces (c and space), then the rest.
        assertEquals(TypingDiff.Change(2, "ocê "), TypingDiff.between("oi vc ", "oi você "))
    }

    @Test
    fun charactersAreCountedNotCodeUnits() {
        assertEquals(TypingDiff.Change(1, "🙂"), TypingDiff.between("a😀", "a🙂"))
    }

    @Test
    fun focusingOnTheCursorPutsItWhereAsked() {
        val t = ViewTransform(2000f, 1000f, 2000, 1000)
        t.focusOn(0.5f, 0.5f, zoom = 2f, atX = 1000f, atY = 250f)
        val (x, y) = t.toView(0.5f, 0.5f)
        assertEquals(1000f, x, 0.5f)
        assertEquals(250f, y, 0.5f)
        assertEquals(2f, t.zoom, 0.001f)
        // Near a corner the view stays filled: the point goes as close as it can.
        t.focusOn(0f, 0f, zoom = 2f, atX = 1000f, atY = 250f)
        assertEquals(0f to 0f, t.toView(0f, 0f))
    }
}
