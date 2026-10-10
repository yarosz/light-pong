package com.yarosz.pong.screen

import com.yarosz.pong.core.SoundEvent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneTest {

    @Test
    fun `each tone has its duration's sample count at 44,100 Hz`() {
        assertEquals(4234, SoundEvent.PADDLE.samples().size)
        assertEquals(706, SoundEvent.WALL.samples().size)
        assertEquals(11334, SoundEvent.POINT.samples().size)
    }

    @Test
    fun `zero crossings give each tone's frequency`() {
        for (event in SoundEvent.entries) {
            val samples = event.samples()
            val crossings = (1 until samples.size).count { (samples[it - 1] >= 0) != (samples[it] >= 0) }
            val measured = crossings / 2.0 / event.durationSeconds()
            assertTrue(abs(measured - event.frequencyHz()) <= 1.0 / event.durationSeconds(), "$event measured $measured Hz")
        }
    }

    @Test
    fun `a 2 ms linear attack and release`() {
        val samples = squareTone(459.0, 0.096)
        val ramp = 88
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        val peak = samples.maxOf { abs(it.toInt()) }
        assertEquals(peak, abs(samples[ramp].toInt()))
        assertEquals(peak, abs(samples[samples.size - 1 - ramp].toInt()))
        assertTrue(abs(samples[ramp / 2].toInt()) in (peak / 2 - 200)..(peak / 2 + 200))
        val attack = (0..ramp).map { abs(samples[it].toInt()) }
        assertEquals(attack.sorted(), attack)
        val release = (samples.size - 1 - ramp until samples.size).map { abs(samples[it].toInt()) }
        assertEquals(release.sortedDescending(), release)
    }

    @Test
    fun `a square wave between two levels in the sustain`() {
        val levels = squareTone(226.0, 0.016).drop(89).dropLast(89).map { abs(it.toInt()) }.toSet()
        assertEquals(1, levels.size)
    }
}
