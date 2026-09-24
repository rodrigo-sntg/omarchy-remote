package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.display.FrameRouter
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRouterTest {
    private fun unit(type: Int) = byteArrayOf(0, 0, 0, 1, type.toByte())
    private val sps = unit(0x67)
    private val slice = unit(0x41)

    @Test
    fun framesAfterANewStreamWaitForTheNewDecoder() {
        val old = mutableListOf<ByteArray>()
        val new = mutableListOf<ByteArray>()
        val router = FrameRouter()
        router.attach(old::add)
        router.deliver(listOf(slice))
        // "screen" arrives: another monitor. Its SPS must not go to the decoder about to be released.
        router.onText("""{"type":"screen","width":1280,"height":720}""")
        router.deliver(listOf(sps, slice))
        assertEquals(1, old.size)
        router.attach(new::add)
        assertEquals(listOf(sps, slice), new)
    }

    @Test
    fun otherMessagesLeaveTheDecoderAlone() {
        val sink = mutableListOf<ByteArray>()
        val router = FrameRouter()
        router.attach(sink::add)
        router.onText("""{"type":"video.pong","t":1}""")
        router.deliver(listOf(slice))
        assertEquals(1, sink.size)
    }

    @Test
    fun withoutADecoderOnlyTheLastKeyframeOnIsKept() {
        val sink = mutableListOf<ByteArray>()
        val router = FrameRouter()
        router.deliver(listOf(slice, sps, slice, sps, slice))
        router.attach(sink::add)
        assertEquals(listOf(sps, slice), sink)
    }
}
