package com.yarosz.pong.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameLogTest {

    @Test
    fun `frames are logged in batches of 60 with steps, draw time and one speed`() {
        val log = FrameLog()
        repeat(59) { assertNull(log.record(16_666_667, 4, 420_000, 900.0)) }
        val line = log.record(16_640_000, 5, 1_234_567, 1169.6)!!
        val pairs = line.split(' ')
        assertEquals(60 * 3 + 1, pairs.size)
        assertEquals(listOf("frame_ms=16.7", "steps=4", "draw_ms=0.42"), pairs.take(3))
        assertEquals(listOf("frame_ms=16.6", "steps=5", "draw_ms=1.23", "speed=1170"), pairs.takeLast(4))
        assertEquals(60, pairs.count { it.startsWith("frame_ms=") })
        assertEquals(1, pairs.count { it.startsWith("speed=") })
        log.record(33_000_000, 8, 0, 0.0)
        log.reset()
        repeat(59) { assertNull(log.record(8_000_000, 2, 50_000, 585.0)) }
        assertEquals(listOf("frame_ms=8.0", "steps=2", "draw_ms=0.05"), log.record(8_000_000, 2, 50_000, 585.0)!!.split(' ').take(3))
    }
}
