package com.yarosz.pong.screen

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldLayoutTest {

    @Test
    fun `the pause target is 144 px square at (540, 88)`() {
        assertTrue(FieldLayout.inPauseTarget(468.0, 16.0))
        assertTrue(FieldLayout.inPauseTarget(612.0, 160.0))
        assertFalse(FieldLayout.inPauseTarget(467.0, 88.0))
        assertFalse(FieldLayout.inPauseTarget(540.0, 161.0))
    }

    @Test
    fun `digits are 72 by 120 segments with a 16 px stroke, centred on their half`() {
        val eight = FieldLayout.scoreRects(8, 270f)
        assertEquals(7, eight.size)
        assertEquals(234f, eight.minOf { it[0] })
        assertEquals(306f, eight.maxOf { it[2] })
        assertEquals(48f, eight.minOf { it[1] })
        assertEquals(168f, eight.maxOf { it[3] })
        assertTrue(eight.all { minOf(it[2] - it[0], it[3] - it[1]) == 16f })
        val eleven = FieldLayout.scoreRects(11, 810f)
        assertEquals(4, eleven.size)
        assertEquals(810f - 84f + 56f, eleven.minOf { it[0] })
        assertEquals(810f + 84f, eleven.maxOf { it[2] })
    }

    @Test
    fun `the letterbox scales uniformly and centres the field`() {
        val exact = Letterbox(1080f, 1168f)
        assertEquals(1f, exact.scale)
        assertEquals(0f, exact.left)
        val wide = Letterbox(2160f + 200f, 2336f)
        assertEquals(2f, wide.scale)
        assertEquals(100f, wide.left)
        assertEquals(540.0, wide.fieldX(1180f))
    }

    @Test
    fun `the pause target in canvas px follows the letterbox`() {
        assertContentEquals(floatArrayOf(468f, 52f, 612f, 196f), Letterbox(1080f, 1240f).pauseTarget())
        assertContentEquals(floatArrayOf(1036f, 32f, 1324f, 320f), Letterbox(2160f + 200f, 2336f).pauseTarget())
    }
}
