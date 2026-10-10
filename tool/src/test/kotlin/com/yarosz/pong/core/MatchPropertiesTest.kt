package com.yarosz.pong.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
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

    /**
     * Random wheel, drag, click, serve, pause and resume calls, the wheel's Detents timed by the Steps
     * taken (several in one Step make a fast spin); two thirds of the scripts also play like
     * a bot. Every fourth script is extreme: it drags the paddle onto the ball every step, even after
     * hitting it, and turns, drags and pauses in bigger bursts.
     */
    private class Script(seed: Long) {
        private val rng = Random(seed xor 0x5EED)
        private val bot = PlayerBot(Random(seed), perfect = seed % 2 == 0L).takeIf { seed % 3 != 0L }
        private val extreme = seed % 4 == 0L
        private val wheel = if (extreme) 13 else 4
        private val dragReach = if (extreme) 400.0 else 60.0
        private var pausedFor = 0
        private var steps = 0L

        fun act(match: Match) {
            steps++
            bot?.act(match)
            if (match.paused) {
                if (--pausedFor <= 0) match.resume()
                return
            }
            if (extreme) match.visibleBall?.let { match.drag(it.cy - Paddle.HALF_LENGTH - match.playerTarget) }
            when (rng.nextInt(1000)) {
                in 0..79 -> {
                    val detents = rng.nextInt(-wheel + 1, wheel)
                    repeat(abs(detents)) { match.turn(down = detents > 0, atMillis = stepMillis(steps)) }
                }
                in 80..99 -> match.drag(rng.nextDouble(-dragReach, dragReach))
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
    fun `the ball's speed changes only on a paddle hit, by one factor of 1_06, once per step`() = forAllSeeds { before, sounds, match ->
        if (before.paused || before.phase != Phase.Rally) return@forAllSeeds
        val hits = sounds.count { it == SoundEvent.PADDLE }
        assertTrue(hits <= 1, "$hits paddle hits in one step")
        val expected = if (hits == 1) min(abs(before.ball.vx) * Ball.SPEED_FACTOR, Ball.MAX_SPEED) else abs(before.ball.vx)
        assertEquals(expected, abs(match.ball.vx), 1e-9, "|vx| after $hits hits")
    }

    @Test
    fun `in a rally the ball's y moves no further than its vertical speed allows`() = forAllSeeds { before, _, match ->
        if (before.paused || before.phase != Phase.Rally || match.phase != Phase.Rally) return@forAllSeeds
        val reach = max(abs(before.ball.vy), abs(match.ball.vy)) * STEP_SECONDS
        val moved = abs(match.ball.cy - before.ball.cy)
        assertTrue(moved <= reach + 1e-6, "cy moved $moved, at most $reach")
    }

    @Test
    fun `each paddle hits the ball at most once between centre-line crossings`() {
        for (seed in 1L..seeds) {
            var hits = 0
            forEachStep(seed) { before, sounds, match ->
                if (before.phase !is Phase.Rally && before.phase !is Phase.Hold) hits = 0
                if ((before.ball.cx < Field.WIDTH / 2) != (match.ball.cx < Field.WIDTH / 2)) hits = 0
                hits += sounds.count { it == SoundEvent.PADDLE }
                assertTrue(hits <= 1, "$hits hits since the centre line")
            }
        }
    }

    @Test
    fun `long sweeps never tunnel, double-hit or leave the field`() {
        val rng = Random(2026)
        val contact = mapOf(Side.PLAYER to PLAYER_CONTACT_X, Side.COMPUTER to Paddle.face(Side.COMPUTER) - Ball.HALF)
        repeat(200_000) { case ->
            val speed = rng.nextDouble(Ball.SERVE_SPEED, Ball.MAX_SPEED)
            val angle = rng.nextDouble(-Ball.MAX_BOUNCE, Ball.MAX_BOUNCE)
            val start = Ball(
                rng.nextDouble(contact.getValue(Side.PLAYER), contact.getValue(Side.COMPUTER)),
                rng.nextDouble(Ball.HALF, Field.HEIGHT - Ball.HALF),
                if (rng.nextBoolean()) speed else -speed,
                speed * kotlin.math.tan(angle),
            )
            val tops = mapOf(Side.PLAYER to rng.nextDouble(0.0, Paddle.MAX_TOP), Side.COMPUTER to rng.nextDouble(0.0, Paddle.MAX_TOP))
            val seconds = rng.nextDouble(0.05, 0.3)
            val result = rallyStep(start, tops.getValue(Side.PLAYER), tops.getValue(Side.COMPUTER), seconds)
            val ball = result.ball
            val context = "case $case: $start for $seconds s, tops $tops -> $ball ${result.sounds}"
            val hits = result.sounds.count { it == SoundEvent.PADDLE }
            assertTrue(hits <= 1, context)
            val expected = if (hits == 1) min(speed * Ball.SPEED_FACTOR, Ball.MAX_SPEED) else speed
            assertEquals(expected, abs(ball.vx), 1e-9, context)
            assertTrue(ball.top >= -eps && ball.bottom <= Field.HEIGHT + eps, "beyond a wall: $context")
            for ((side, top) in tops) {
                val inside = ball.right > Paddle.left(side) + eps && ball.left < Paddle.left(side) + Paddle.THICKNESS - eps &&
                    ball.bottom > top + eps && ball.top < top + Paddle.LENGTH - eps
                assertTrue(!inside, "inside the $side paddle: $context")
                val x = contact.getValue(side)
                val crossed = if (side == Side.PLAYER) start.vx < 0 && ball.cx < x else start.vx > 0 && ball.cx > x
                if (crossed && hits == 0) {
                    val y = interceptY(start, x)
                    assertTrue(y <= top - Ball.HALF + eps || y >= top + Paddle.LENGTH + Ball.HALF - eps, "crossed the $side face at y $y: $context")
                }
            }
        }
    }

    @Test
    fun `the player paddle and its target stay within 0 to 976`() = forAllSeeds { _, _, match ->
        assertTrue(match.playerTarget in 0.0..Paddle.MAX_TOP, "target ${match.playerTarget}")
        assertTrue(match.playerTop in 0.0..Paddle.MAX_TOP, "top ${match.playerTop}")
    }

    @Test
    fun `the computer never moves faster than its max speed`() {
        for (seed in 1L..seeds) {
            val maxStep = Difficulty.entries[(seed % 3).toInt()].maxSpeed / STEPS_PER_SECOND
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
        val match = Match(seed, Difficulty.entries[(seed % 3).toInt()])
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
