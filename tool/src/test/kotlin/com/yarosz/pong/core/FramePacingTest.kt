package com.yarosz.pong.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 60 Hz frames whose vsync timestamps jitter by up to [JITTER_NANOS] must run 4 Steps each. The Steps
 * are counted with [Match.phaseSteps] on the player's serve, which counts every Step and moves nothing.
 */
class FramePacingTest {

    @Test
    fun `jittered 60 Hz frames run 4 Steps each`() {
        val histogram = stepsPerFrame(Match(1, Difficulty.NORMAL), jitteredIntervals(Random(7), FRAMES))
        println("steps per frame (60 Hz, +-50 us vsync jitter, $FRAMES frames): $histogram")
        assertEquals(mapOf(4 to FRAMES), histogram)
    }

    @Test
    fun `at the speed cap the drawn Ball moves the same distance every frame`() {
        for (period in listOf(PERIOD_NANOS, 16_600_000L, 16_683_000L)) {
            val match = Match(3, Difficulty.HARD)
            val drawn = mutableListOf<Double>()
            val stepped = mutableListOf<Double>()
            match.holdAtCap()
            match.advance(PERIOD_NANOS)
            for (interval in jitteredIntervals(Random(period), FRAMES, period)) {
                match.holdAtCap()
                val drawnBefore = match.drawnBall!!
                val before = match.ball
                match.advance(interval)
                val drawnAfter = match.drawnBall!!
                assertEquals(Ball.MAX_SPEED, abs(match.ball.vx))
                if (listOf(drawnBefore, drawnAfter, before, match.ball).all { it.cx in CLEAR_OF_PADDLES }) {
                    drawn += abs(drawnAfter.cx - drawnBefore.cx)
                    stepped += abs(match.ball.cx - before.cx)
                }
            }
            println("period $period ns: drawn px/frame ${drawn.min()}..${drawn.max()} (n=${drawn.size}), stepped ${stepped.min()}..${stepped.max()}")
            assertTrue(drawn.size > FRAMES / 2)
            assertTrue(drawn.max() - drawn.min() < 1.0, "period $period: ${drawn.min()}..${drawn.max()}")
        }
    }

    @Test
    fun `pausing leaves the drawn picture where it was`() {
        val match = Match(9, Difficulty.NORMAL)
        match.serve()
        match.drag(150.0)
        match.advance(PERIOD_NANOS + 1_300_000)
        val ball = match.drawnBall
        val playerTop = match.drawnPlayerTop
        val computerTop = match.drawnComputerTop
        match.pause()
        assertEquals(ball, match.drawnBall)
        assertEquals(playerTop, match.drawnPlayerTop)
        assertEquals(computerTop, match.drawnComputerTop)
    }

    @Test
    fun `the drawn Ball does not sweep back to the centre after a Point`() {
        val match = Match(5, Difficulty.EASY)
        match.serve()
        match.stepUntil { phase is Phase.PointPause }
        match.stepUntil { phase !is Phase.PointPause }
        repeat(3) { match.advance(4_000_000) }
        assertEquals(Ball.AT_CENTRE.cx, match.drawnBall!!.cx)
        assertEquals(Ball.AT_CENTRE.cy, match.drawnBall!!.cy)
    }

    /** Keeps a Rally going at the speed cap: serves if a serve is pending, and puts both Paddles on the Ball's line. */
    private fun Match.holdAtCap() {
        if (phase is Phase.PlayerServe) serve()
        val factor = Ball.MAX_SPEED / abs(ball.vx)
        val capped = ball.copy(vx = ball.vx * factor, vy = ball.vy * factor)
        place(capped, Paddle.clampTop(capped.cy - Paddle.HALF_LENGTH))
    }

    private fun stepsPerFrame(match: Match, intervals: List<Long>): Map<Int, Int> {
        val counts = sortedMapOf<Int, Int>()
        for (interval in intervals) {
            val before = match.phaseSteps
            match.advance(interval)
            check(match.phase == Phase.PlayerServe && !match.paused)
            counts.merge(match.phaseSteps - before, 1, Int::plus)
        }
        return counts
    }

    private companion object {
        const val FRAMES = 3_000
        const val PERIOD_NANOS = 16_666_667L
        const val JITTER_NANOS = 50_000L

        /** Ball centres whose frame cannot include a Paddle bounce: more than one frame's travel from either contact x. */
        val CLEAR_OF_PADDLES = PLAYER_CONTACT_X + 30..Field.WIDTH - PLAYER_CONTACT_X - 30

        /** Frame intervals between vsync timestamps i * period + jitter_i. */
        fun jitteredIntervals(rng: Random, frames: Int, period: Long = PERIOD_NANOS): List<Long> {
            val stamps = List(frames + 1) { it * period + rng.nextLong(-JITTER_NANOS, JITTER_NANOS + 1) }
            return stamps.zipWithNext { a, b -> b - a }
        }
    }
}
