package com.yarosz.pong.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DESIGN.md item 20 and decision log D31: the rate-based step and the Sensitivity setting. */
class WheelTest {

    private companion object {
        const val EPS = 1e-9
    }

    @Test
    fun `the step curve at 0, 10, 25, 40 and 60 Detents a second`() {
        val rates = listOf(0.0, 10.0, 25.0, 40.0, 60.0)
        assertEquals(listOf(40.0, 40.0, 60.0, 80.0, 80.0), rates.map { Sensitivity.NORMAL.step(it) })
        assertEquals(listOf(60.0, 60.0, 90.0, 120.0, 120.0), rates.map { Sensitivity.FAST.step(it) })
    }

    @Test
    fun `the Detent rate counts the Detents of the last 100 ms, this one included`() {
        val rate = DetentRate()
        assertEquals(0.0, rate.detent(1_000), "a lone Detent")
        assertEquals(10.0, rate.detent(1_100), "two Detents 100 ms apart")
        assertEquals(20.0, rate.detent(1_150), "three Detents over 100 ms")
        assertEquals(0.0, rate.detent(1_400), "the window has emptied")
        assertEquals(1_000.0, rate.detent(1_400), "two Detents in the same ms")
    }

    @Test
    fun `a steady spin reads as its own rate`() {
        for (perSecond in listOf(12, 20, 25, 40)) {
            val rate = DetentRate()
            val gap = 1_000L / perSecond
            var last = 0.0
            repeat(20) { last = rate.detent(it * gap) }
            assertEquals(1_000.0 / gap, last, 1e-9, "$perSecond a second")
        }
    }

    @Test
    fun `every Detent moves the target 40 to 80 px, or 60 to 120 px when Fast, and the target stays on the Field`() {
        for (sensitivity in Sensitivity.entries) for (seed in 1L..200) {
            val rng = Random(seed)
            val match = Match(seed, Difficulty.NORMAL, sensitivity)
            var millis = 0L
            repeat(400) {
                millis += if (rng.nextInt(4) == 0) rng.nextLong(0, 400) else rng.nextLong(0, 60)
                val down = rng.nextBoolean()
                val before = match.playerTarget
                val step = sensitivity.step(checkNotNull(match.turn(down, millis)))
                match.step()
                val moved = abs(match.playerTarget - before)
                val atEnd = match.playerTarget == 0.0 || match.playerTarget == Paddle.MAX_TOP
                val range = Sensitivity.SLOW_STEP * sensitivity.factor - EPS..Sensitivity.FAST_STEP * sensitivity.factor + EPS
                assertTrue(step in range, "seed $seed $sensitivity step $step")
                assertTrue(abs(moved - step) < EPS || (atEnd && moved <= step), "seed $seed $sensitivity moved $moved for a $step px step")
                assertTrue(match.playerTarget in 0.0..Paddle.MAX_TOP)
            }
        }
    }

    @Test
    fun `a spin of 10 Detents a second or slower moves 40 px a Detent, as 0_1_0 did`() {
        for (seed in 1L..50) {
            val rng = Random(seed)
            val match = Match(seed, Difficulty.NORMAL)
            var expected = match.playerTarget
            var millis = 0L
            repeat(300) {
                millis += rng.nextLong(100, 2_000)
                val down = rng.nextBoolean()
                match.turn(down, millis)
                match.step()
                expected = Paddle.clampTop(expected + if (down) 40.0 else -40.0)
                assertEquals(expected, match.playerTarget, "seed $seed")
            }
        }
    }

    @Test
    fun `a fast spin moves further than a slow one`() {
        val slow = Match(1, Difficulty.NORMAL)
        val fast = Match(1, Difficulty.NORMAL)
        repeat(6) {
            slow.turn(down = true, atMillis = it * 200L)
            fast.turn(down = true, atMillis = it * 40L)
        }
        slow.step()
        fast.step()
        assertEquals(Paddle.CENTRE_TOP + 6 * 40, slow.playerTarget)
        assertEquals(Paddle.CENTRE_TOP + 40 + 5 * 60, fast.playerTarget, "the first Detent of a spin is lone; 25 a second earns 60 px")
    }

    @Test
    fun `turn returns the Detent rate it applied, and nothing outside play`() {
        val match = Match(1, Difficulty.NORMAL)
        val rate = DetentRate()
        for (at in listOf(0L, 40L, 80L, 300L, 310L, 320L)) assertEquals(rate.detent(at), match.turn(down = true, atMillis = at))
        match.pause()
        assertNull(match.turn(down = true, atMillis = 330))
    }
}
