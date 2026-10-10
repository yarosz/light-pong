package com.yarosz.pong.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameLogTest {

    @Test
    fun `frame intervals are logged in batches of 60 tenths of a millisecond`() {
        val log = FrameLog()
        repeat(59) { assertNull(log.record(16_666_667)) }
        val line = log.record(16_640_000)!!
        val values = line.split(' ')
        assertEquals(60, values.size)
        assertEquals("frame_ms=16.7", values.first())
        assertEquals("frame_ms=16.6", values.last())
        log.record(33_000_000)
        log.reset()
        repeat(59) { assertNull(log.record(8_000_000)) }
        assertEquals("frame_ms=8.0", log.record(8_000_000)!!.split(' ').first())
    }
}
