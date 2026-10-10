package com.yarosz.pong.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hundreds of seeded matches, each driven by a seeded random input script, with the invariants checked
 * after every step.
 */
class MatchPropertiesTest {

    private val seeds = 400
    private val stepsPerMatch = 30_000
    private val eps = 1e-6

    /** Random wheel, drag, click, serve, pause and resume calls; half the scripts also play like a bot. */
    private class Script(seed: Long) {
        private val rng = Random(seed xor 0x5EED)
        private val bot = PlayerBot(Random(seed), perfect = seed % 2 == 0L).takeIf { seed % 3 != 0L }
        private var pausedFor = 0

        fun act(match: Match) {
            if (match.paused) {
                if (--pausedFor <= 0) match.resume()
                return
            }
            bot?.act(match)
            when (rng.nextInt(1000)) {
                in 0..79 -> match.turn(rng.nextInt(-3, 4))
                in 80..99 -> match.drag(rng.nextDouble(-60.0, 60.0))
                in 100..102 -> match.click()
                in 103..112 -> match.serve()
                113 -> {
                    match.pause()
                    pausedFor = rng.nextInt(1, 40)
                }
            }
        }
    }

    private fun forEachStep(seed: Long, check: (before: Snapshot, sounds: List<SoundEvent>, match: Match) -> Unit) {
        val match = Match(seed, Difficulty.entries[(seed % 3).toInt()])
        val script = Script(seed)
        repeat(stepsPerMatch) {
            if (match.phase is Phase.MatchOver) return
            script.act(match)
            val before = match.snapshot()
            val sounds = match.step()
            try {
                check(before, sounds, match)
            } catch (e: AssertionError) {
                fail("seed $seed: ${e.message}\nbefore $before\nafter ${match.snapshot()}\nsounds $sounds", e)
            }
        }
    }

    private fun forAllSeeds(check: (before: Snapshot, sounds: List<SoundEvent>, match: Match) -> Unit) {
        for (seed in 1L..seeds) forEachStep(seed, check)
    }

    @Test
    fun `the ball stays in the field except across a goal line, and never inside a paddle`() = forAllSeeds { before, _, match ->
        if (before.phase != Phase.Rally || match.phase != Phase.Rally) return@forAllSeeds
        val ball = match.ball
        assertTrue(ball.top >= -eps && ball.bottom <= Field.HEIGHT + eps, "ball beyond a wall")
        assertTrue(ball.left > 0 && ball.right < Field.WIDTH, "ball across a goal line still in play")
        for ((side, top) in listOf(Side.PLAYER to match.playerTop, Side.COMPUTER to match.computerTop)) {
            val inside = ball.right > Paddle.left(side) + eps && ball.left < Paddle.left(side) + Paddle.THICKNESS - eps &&
                ball.bottom > top + eps && ball.top < top + Paddle.LENGTH - eps
            assertTrue(!inside, "ball inside the $side paddle")
        }
    }

    @Test
    fun `in play, the ball's horizontal speed and angle stay in range`() = forAllSeeds { _, _, match ->
        if (match.phase != Phase.Rally) return@forAllSeeds
        val ball = match.ball
        assertTrue(abs(ball.vx) >= Ball.SERVE_SPEED - eps && abs(ball.vx) <= Ball.MAX_SPEED + eps, "vx ${ball.vx}")
        val degrees = atan(abs(ball.vy) / abs(ball.vx)) * 180 / PI
        assertTrue(degrees <= 50 + eps, "angle $degrees")
    }

    @Test
    fun `scores never fall, rise by one per point, and the match ends exactly at 11`() = forAllSeeds { before, sounds, match ->
        val gained = (match.playerScore - before.playerScore) to (match.computerScore - before.computerScore)
        assertTrue(gained in setOf(0 to 0, 1 to 0, 0 to 1), "score change $gained")
        assertEquals(gained != (0 to 0), SoundEvent.POINT in sounds, "a point sounds exactly when scored")
        assertTrue(match.playerScore <= 11 && match.computerScore <= 11)
        val phase = match.phase
        if (phase is Phase.MatchOver) {
            val (winner, loser) = if (phase.winner == Side.PLAYER) match.playerScore to match.computerScore else match.computerScore to match.playerScore
            assertEquals(11, winner)
            assertTrue(loser < 11)
        } else if (phase !is Phase.PointPause) {
            assertTrue(match.playerScore < 11 && match.computerScore < 11, "play continued after 11")
        }
    }

    @Test
    fun `after a paddle hit the ball moves away from that paddle`() = forAllSeeds { _, sounds, match ->
        if (SoundEvent.PADDLE !in sounds || match.phase != Phase.Rally) return@forAllSeeds
        val ball = match.ball
        if (ball.cx < Field.WIDTH / 2) assertTrue(ball.vx > 0, "player hit, vx ${ball.vx}")
        else assertTrue(ball.vx < 0, "computer hit, vx ${ball.vx}")
    }

    @Test
    fun `the player paddle and its target stay within 0 to 976`() = forAllSeeds { _, _, match ->
        assertTrue(match.playerTarget in 0.0..Paddle.MAX_TOP, "target ${match.playerTarget}")
        assertTrue(match.playerTop in 0.0..Paddle.MAX_TOP, "top ${match.playerTop}")
    }

    @Test
    fun `the computer never moves faster than its max speed`() {
        for (seed in 1L..seeds) {
            val maxStep = Difficulty.entries[(seed % 3).toInt()].maxSpeed / Match.STEPS_PER_SECOND
            forEachStep(seed) { before, _, match ->
                assertTrue(abs(match.computerTop - before.computerTop) <= maxStep + 1e-9, "moved ${match.computerTop - before.computerTop}")
                assertTrue(match.computerTop in 0.0..Paddle.MAX_TOP)
            }
        }
    }

    @Test
    fun `simulation time does not advance while paused`() = forAllSeeds { before, sounds, match ->
        if (!before.paused) return@forAllSeeds
        assertEquals(emptyList(), sounds)
        assertEquals(before, match.snapshot())
        assertEquals(emptyList(), match.advance(Match.MAX_FRAME_NANOS))
        assertEquals(before, match.snapshot())
    }

    private fun record(seed: Long, frameRng: Random): List<Snapshot> {
        val match = Match(seed, Difficulty.HARD)
        val script = Script(seed)
        return (1..3_000).map {
            script.act(match)
            match.advance(frameRng.nextLong(1_000_000, 60_000_000))
            match.snapshot()
        }
    }

    @Test
    fun `the same seed and inputs replay identically, bit for bit`() {
        for (seed in 1L..100) {
            assertEquals(record(seed, Random(seed)), record(seed, Random(seed)), "seed $seed")
        }
    }

    @Test
    fun `different seeds give different matches`() {
        val firstServes = (1L..200).map { seed ->
            Match(seed, Difficulty.NORMAL).apply { serve() }.ball
        }
        assertEquals(firstServes.size, firstServes.toSet().size)
        assertNotEquals(record(1, Random(0)), record(2, Random(0)))
    }
}
