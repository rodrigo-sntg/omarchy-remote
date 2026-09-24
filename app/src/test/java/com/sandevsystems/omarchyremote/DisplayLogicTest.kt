package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.AccessUnitAssembler
import com.sandevsystems.omarchyremote.display.AnnexBSplitter
import com.sandevsystems.omarchyremote.display.displayRequest
import com.sandevsystems.omarchyremote.display.normalizeTouch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayLogicTest {
    private fun bytes(vararg v: Int) = v.map(Int::toByte).toByteArray()

    @Test
    fun splitsNalUnitsAcrossChunksWithBothStartCodeLengths() {
        val splitter = AnnexBSplitter()
        val first = splitter.feed(bytes(0, 0, 0, 1, 0x67, 1, 2, 0, 0, 1, 0x68, 3))
        assertEquals(1, first.size)
        assertArrayEquals(bytes(0, 0, 0, 1, 0x67, 1, 2), first[0])
        // The PPS unit is complete only when the next start code arrives, split over two chunks.
        assertEquals(0, splitter.feed(bytes(4, 0, 0)).size)
        val second = splitter.feed(bytes(0, 1, 0x65, 9))
        assertEquals(1, second.size)
        assertArrayEquals(bytes(0, 0, 1, 0x68, 3, 4), second[0])
    }

    @Test
    fun nalTypeIsReadAfterTheStartCode() {
        assertEquals(7, AnnexBSplitter.nalType(bytes(0, 0, 0, 1, 0x67)))
        assertEquals(5, AnnexBSplitter.nalType(bytes(0, 0, 1, 0x65)))
    }

    @Test
    fun touchOnLetterboxedVideoIsNormalized() {
        // 2340x1080 video fitted into a 2400x1080 view: 30 px bars left and right.
        assertEquals(0f to 0f, normalizeTouch(30f, 0f, 2400f, 1080f, 2340, 1080))
        val (x, y) = normalizeTouch(1200f, 540f, 2400f, 1080f, 2340, 1080)!!
        assertEquals(0.5f, x, 0.001f)
        assertEquals(0.5f, y, 0.001f)
        assertNull(normalizeTouch(10f, 500f, 2400f, 1080f, 2340, 1080)) // on the bar
    }

    @Test
    fun displayRequestUsesLandscapeEvenSizeAndReadableScale() {
        assertEquals(Triple(2340, 1080, 1.5), displayRequest(1080, 2340))
        assertEquals(Triple(3120, 1440, 2.0), displayRequest(1440, 3120))
        assertEquals(Triple(1280, 720, 1.0), displayRequest(1281, 721))
    }

    @Test
    fun slicesAreGroupedIntoWholeFrames() {
        val assembler = AccessUnitAssembler()
        // first_mb_in_slice == 0 is ue(v) "1": the top bit of the byte after the NAL header.
        val sps = bytes(0, 0, 0, 1, 0x67, 0x42)
        val pps = bytes(0, 0, 0, 1, 0x68, 0xce)
        val idrFirst = bytes(0, 0, 1, 0x65, 0x88, 1)
        val idrNext = bytes(0, 0, 1, 0x65, 0x40, 2) // first_mb_in_slice != 0
        val pFirst = bytes(0, 0, 1, 0x41, 0x9a, 3)
        val pNext = bytes(0, 0, 1, 0x41, 0x20, 4)
        val nextSps = bytes(0, 0, 0, 1, 0x67, 0x42)

        assertEquals(0, listOf(sps, pps, idrFirst, idrNext).sumOf { assembler.add(it).size })
        val frame1 = assembler.add(pFirst)
        assertEquals(1, frame1.size)
        assertArrayEquals(sps + pps + idrFirst + idrNext, frame1[0])
        assertEquals(0, assembler.add(pNext).size)
        val frame2 = assembler.add(nextSps)
        assertArrayEquals(pFirst + pNext, frame2.single())
    }

    @Test
    fun theHostsEndOfFrameMarkerReleasesTheLastFrameAtOnce() {
        // A still PC screen sends one frame and then nothing: the host marks its end with an AUD.
        val splitter = AnnexBSplitter()
        val assembler = AccessUnitAssembler()
        val sps = bytes(0, 0, 0, 1, 0x67, 0x42)
        val idr = bytes(0, 0, 1, 0x65, 0x88, 1)
        val aud = bytes(0, 0, 0, 1, 0x09, 0xf0)
        assertEquals(0, splitter.feed(sps + idr).flatMap(assembler::add).size)
        val frames = splitter.feed(aud).flatMap(assembler::add)
        assertArrayEquals(sps + idr, frames.single())
    }

    @Test
    fun anIncompleteMarkerIsHeldUntilItIsWhole() {
        val splitter = AnnexBSplitter()
        splitter.feed(bytes(0, 0, 1, 0x65, 0x88, 1))
        assertEquals(1, splitter.feed(bytes(0, 0, 0, 1, 0x09)).size) // the slice; the marker is not whole
        assertArrayEquals(bytes(0, 0, 0, 1, 0x09, 0xf0), splitter.feed(bytes(0xf0)).single())
    }
}
