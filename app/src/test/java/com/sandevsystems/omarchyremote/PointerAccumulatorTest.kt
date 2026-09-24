package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.PointerAccumulator
import com.sandevsystems.omarchyremote.input.ScrollAccumulator
import org.junit.Assert.assertEquals
import org.junit.Test

class PointerAccumulatorTest {
    @Test
    fun slowSubpixelMotionIsNotLost() {
        val accumulator = PointerAccumulator()
        val moves = List(12) { accumulator.add(0.25f, -0.25f) }
        assertEquals(3, moves.sumOf { it.first })
        assertEquals(-3, moves.sumOf { it.second })
    }

    @Test
    fun signChangeKeepsTheRunningSum() {
        val accumulator = PointerAccumulator()
        assertEquals(0 to 0, accumulator.add(0.7f, 0f))
        assertEquals(0 to 0, accumulator.add(-1.4f, 0f)) // running total -0.7
        assertEquals(-1 to 0, accumulator.add(-0.4f, 0f)) // running total -1.1
    }

    @Test
    fun manySmallEventsSumLikeOneFrame() {
        val accumulator = PointerAccumulator()
        val total = List(100) { accumulator.add(1.25f, 0.5f) }
        assertEquals(125, total.sumOf { it.first })
        assertEquals(50, total.sumOf { it.second })
    }

    @Test
    fun resetDropsRemainder() {
        val accumulator = PointerAccumulator()
        accumulator.add(0.9f, 0.9f)
        accumulator.reset()
        assertEquals(0 to 0, accumulator.add(0.2f, 0.2f))
    }

    @Test
    fun scrollEmitsWholeStepsAtThreshold() {
        val scroll = ScrollAccumulator(stepDistance = 10f)
        assertEquals(0, scroll.add(6f))
        assertEquals(1, scroll.add(6f)) // 12, remainder 2
        assertEquals(-2, scroll.add(-25f)) // -23, remainder -3
        scroll.reset()
        assertEquals(0, scroll.add(9f))
    }
}
