package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.AnsiRun
import com.sandevsystems.omarchyremote.network.AnsiStyle
import com.sandevsystems.omarchyremote.network.ansiRuns
import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiTest {
    private val esc = "\u001b"

    @Test
    fun theSelectedTabIsInverseAndTheRestPlain() {
        assertEquals(
            listOf(AnsiRun("   ", AnsiStyle()), AnsiRun(" Status ", AnsiStyle(inverse = true)), AnsiRun("  Config", AnsiStyle())),
            ansiRuns("   $esc[7m Status $esc[0m  Config"),
        )
    }

    @Test
    fun colorsInEachOfTheirForms() {
        val runs = ansiRuns("$esc[38;2;255;193;7mamarelo$esc[39m $esc[38;5;6mciano$esc[0m $esc[1;31mvermelho$esc[22m$esc[94mazul$esc[48;2;0;0;0mfundo$esc[49m")
        assertEquals(AnsiRun("amarelo", AnsiStyle(fg = 0xFFC107)), runs[0])
        assertEquals(AnsiRun(" ", AnsiStyle()), runs[1])
        assertEquals(AnsiStyle(fg = 0x06989A), runs[2].style)
        assertEquals(AnsiStyle(fg = 0xCC0000, bold = true), runs[4].style)
        assertEquals(AnsiStyle(fg = 0x729FCF), runs[5].style)
        assertEquals(AnsiStyle(fg = 0x729FCF, bg = 0x000000), runs[6].style)
    }

    @Test
    fun otherEscapesAndCarriageReturnsGoAway() {
        assertEquals(listOf(AnsiRun("a\nb", AnsiStyle(dim = true))), ansiRuns("$esc]0;title\u0007$esc[2ma\r\n$esc[?25lb"))
    }
}
