package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.ImageRefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageRefsTest {
    @Test
    fun pathsToImagesInWhatTheAgentSaid() {
        val text = "Os arquivos estão em `design/logo/comparar.png` e veja /home/u/x/shot.webp, e logo.SVG."
        assertEquals(listOf("design/logo/comparar.png", "/home/u/x/shot.webp", "logo.SVG"), ImageRefs.inText(text))
    }

    @Test
    fun eachOnceAndAtMostSix() {
        assertEquals(listOf("a.png"), ImageRefs.inText("a.png e de novo `a.png`"))
        assertEquals(6, ImageRefs.inText((1..9).joinToString(" ") { "f$it.png" }).size)
    }

    @Test
    fun linksAndOtherFilesAreNotImages() {
        assertEquals(emptyList<String>(), ImageRefs.inText("veja https://site.com/x.png e o arquivo notes.txt e x.png.bak"))
    }

    @Test
    fun anImageTheAgentReadOrWrote() {
        assertEquals("/home/u/p/shot.png", ImageRefs.ofTool("Read", "/home/u/p/shot.png"))
        assertEquals("logo.svg", ImageRefs.ofTool("Write", "logo.svg"))
        assertNull(ImageRefs.ofTool("Read", "/home/u/p/App.kt"))
        assertNull(ImageRefs.ofTool("Bash", "rm x.png"))
    }
}
