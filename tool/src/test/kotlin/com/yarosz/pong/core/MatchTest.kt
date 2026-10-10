package com.yarosz.pong.core

import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchTest {

    private fun nanos(seconds: Double) = (seconds * Match.NANOS_PER_SECOND).roundToLong()

    private fun newMatch(seed: Long = 42, difficulty: Difficulty = Difficulty.NORMAL) = Match(seed, difficulty)

    /** Plays as a player who always misses until [done]. */
    private fun Match.loseUntil(done: Match.() -> Boolean) {
        val bot = PlayerBot(Random(seed), perfect = false, serveDelaySteps = 1)
        stepUntil { bot.act(this); done() }
    }

    private fun Match.toComputerServe() = loseUntil { phase is Phase.ComputerServe }

    @Test
    fun `a match starts with both paddles centred and the player's serve pending`() {
        val match = newMatch()
        assertEquals(Phase.PlayerServe, match.phase)
        assertEquals(Paddle.CENTRE_TOP, match.playerTop)
        assertEquals(Paddle.CENTRE_TOP, match.computerTop)
        assertEquals(488.0, Paddle.CENTRE_TOP)
        assertEquals(Ball.AT_CENTRE, match.visibleBall)
        assertTrue(match.servePromptShown)
        assertTrue(match.keepScreenOn)
    }

    @Test
    fun `serving launches the ball from the centre toward the computer at 10 to 30 degrees`() {
        val match = newMatch()
        match.serve()
        assertEquals(Phase.Rally, match.phase)
        val ball = match.ball
        assertEquals(Ball.SERVE_SPEED, ball.vx)
        val degrees = Math.toDegrees(kotlin.math.atan(kotlin.math.abs(ball.vy) / ball.vx))
        assertTrue(degrees in 10.0..30.0, "$degrees")
        assertFalse(match.servePromptShown)
    }

    @Test
    fun `the computer serves exactly 0_8 s after its serve state begins`() {
        val match = newMatch()
        match.toComputerServe()
        assertEquals(Phase.ComputerServe, match.phase)
        match.steps(Match.COMPUTER_SERVE_STEPS - 1)
        assertIs<Phase.ComputerServe>(match.phase)
        assertEquals(Ball.AT_CENTRE, match.ball)
        match.step()
        assertEquals(Phase.Rally, match.phase)
        assertEquals(-Ball.SERVE_SPEED, match.ball.vx)
        assertEquals(192, (0.8 * STEPS_PER_SECOND).roundToLong().toInt())
    }

    @Test
    fun `the player serves first and the serve alternates every point`() {
        val match = newMatch(seed = 5)
        val servers = mutableListOf<Side>()
        val bot = PlayerBot(Random(5), perfect = false, serveDelaySteps = 1)
        var last: Phase? = null
        while (match.phase !is Phase.MatchOver) {
            val phase = match.phase
            if (last !is Phase.PlayerServe && phase is Phase.PlayerServe) servers += Side.PLAYER
            if (last !is Phase.ComputerServe && phase is Phase.ComputerServe) servers += Side.COMPUTER
            last = phase
            bot.act(match)
            match.step()
        }
        assertEquals(List(servers.size) { if (it % 2 == 0) Side.PLAYER else Side.COMPUTER }, servers)
        assertEquals(match.playerScore + match.computerScore, servers.size)
    }

    @Test
    fun `a point hides the ball for 0_6 s, then the next serve`() {
        val match = newMatch()
        match.loseUntil { phase is Phase.PointPause }
        assertEquals(1, match.computerScore)
        assertNull(match.visibleBall)
        assertTrue(match.keepScreenOn)
        match.steps(Match.PAUSE_STEPS - 1)
        assertIs<Phase.PointPause>(match.phase)
        match.step()
        assertEquals(Phase.ComputerServe, match.phase)
        assertEquals(Ball.AT_CENTRE, match.visibleBall)
    }

    @Test
    fun `the point sound plays on the crossing step`() {
        val match = newMatch()
        match.serve()
        var last = emptyList<SoundEvent>()
        while (match.phase !is Phase.PointPause) last = match.step()
        assertEquals(SoundEvent.POINT, last.last())
    }

    @Test
    fun `the match ends at 11 with no win by two, 0_6 s after the winning point`() {
        val match = newMatch()
        match.loseUntil { computerScore == Match.WINNING_SCORE }
        assertEquals(0, match.playerScore)
        assertIs<Phase.PointPause>(match.phase)
        match.steps(Match.PAUSE_STEPS - 1)
        assertIs<Phase.PointPause>(match.phase)
        match.step()
        assertEquals(Phase.MatchOver(Side.COMPUTER), match.phase)
        assertNull(match.visibleBall)
        assertFalse(match.keepScreenOn)
        val frozen = match.snapshot()
        assertEquals(emptyList(), match.advance(nanos(1.0)))
        match.turnSlowly(3)
        match.click()
        match.pause()
        assertEquals(frozen, match.snapshot())
    }

    @Test
    fun `resuming a rally holds the ball frozen and visible for 0_6 s`() {
        val match = newMatch()
        match.serve()
        match.steps(30)
        match.click()
        assertTrue(match.paused)
        assertFalse(match.keepScreenOn)
        val frozen = match.ball
        match.resume()
        assertEquals(Phase.Hold, match.phase)
        assertTrue(match.keepScreenOn)
        val computerBefore = match.computerTop
        match.turnSlowly(-3)
        match.steps(Match.PAUSE_STEPS - 1)
        assertEquals(frozen, match.visibleBall)
        assertTrue(match.playerTop < Paddle.CENTRE_TOP)
        assertTrue(match.computerTop != computerBefore)
        match.step()
        assertEquals(Phase.Rally, match.phase)
        match.step()
        assertTrue(match.ball != frozen)
    }

    @Test
    fun `a click during the hold pauses again, and resuming restarts the hold`() {
        val match = newMatch()
        match.serve()
        match.pause()
        match.resume()
        match.steps(100)
        match.click()
        assertTrue(match.paused)
        match.resume()
        assertEquals(Phase.Hold, match.phase)
    }

    @Test
    fun `resuming the player's serve waits again and restarts the idle timer`() {
        val match = newMatch()
        match.steps(Match.IDLE_PAUSE_STEPS - 10)
        match.pause()
        match.resume()
        assertEquals(Phase.PlayerServe, match.phase)
        match.steps(Match.IDLE_PAUSE_STEPS - 1)
        assertFalse(match.paused)
        match.step()
        assertTrue(match.paused)
    }

    @Test
    fun `resuming the computer's serve restarts the full 0_8 s delay`() {
        val match = newMatch()
        match.toComputerServe()
        match.steps(150)
        match.click()
        assertTrue(match.paused)
        match.resume()
        assertEquals(Phase.ComputerServe, match.phase)
        match.steps(Match.COMPUTER_SERVE_STEPS - 1)
        assertIs<Phase.ComputerServe>(match.phase)
        match.step()
        assertEquals(Phase.Rally, match.phase)
    }

    @Test
    fun `resuming a point pause goes straight to the next serve`() {
        val match = newMatch()
        match.loseUntil { phase is Phase.PointPause }
        match.steps(10)
        match.pause()
        match.resume()
        assertEquals(Phase.ComputerServe, match.phase)
    }

    @Test
    fun `resuming the winning point's pause goes straight to Match over`() {
        val match = newMatch()
        match.loseUntil { computerScore == Match.WINNING_SCORE }
        match.pause()
        match.resume()
        assertEquals(Phase.MatchOver(Side.COMPUTER), match.phase)
    }

    @Test
    fun `clicks during a point pause are ignored`() {
        val match = newMatch()
        match.loseUntil { phase is Phase.PointPause }
        match.click()
        assertFalse(match.paused)
    }

    @Test
    fun `the click serves, pauses or does nothing by phase`() {
        val match = newMatch()
        match.click()
        assertEquals(Phase.Rally, match.phase)
        match.click()
        assertTrue(match.paused)
        match.click()
        assertTrue(match.paused)
        assertEquals(Phase.Rally, match.phase)
    }

    @Test
    fun `60 s without input on the player's serve pauses the match`() {
        val match = newMatch()
        match.steps(Match.IDLE_PAUSE_STEPS - 1)
        assertFalse(match.paused)
        match.step()
        assertTrue(match.paused)
        assertIs<Phase.PlayerServe>(match.phase)
        assertFalse(match.keepScreenOn)
    }

    @Test
    fun `any paddle input restarts the idle timer`() {
        val match = newMatch()
        match.steps(Match.IDLE_PAUSE_STEPS - 1)
        match.turnSlowly(1)
        match.steps(Match.IDLE_PAUSE_STEPS - 1)
        match.drag(-5.0)
        match.steps(Match.IDLE_PAUSE_STEPS - 1)
        assertFalse(match.paused)
        match.step()
        assertTrue(match.paused)
    }

    @Test
    fun `a frame longer than 50 ms runs only 12 steps`() {
        val match = newMatch()
        match.advance(nanos(1.0))
        assertEquals(12, match.phaseSteps)
        match.advance(nanos(0.05))
        assertEquals(24, match.phaseSteps)
    }

    @Test
    fun `frames carry their remainder, from half a step, so 60 Hz frames average 4 steps`() {
        val match = newMatch()
        repeat(60) { match.advance(nanos(1.0 / 60)) }
        assertEquals(240, match.phaseSteps)
        repeat(3) { match.advance(nanos(0.001)) }
        assertEquals(241, match.phaseSteps)
    }

    @Test
    fun `a detent moves the target 40 px and the paddle eases there and snaps`() {
        val match = newMatch()
        match.turnSlowly(1)
        assertEquals(Paddle.CENTRE_TOP, match.playerTarget)
        match.step()
        assertEquals(Paddle.CENTRE_TOP + 40, match.playerTarget)
        val firstStep = match.playerTop - Paddle.CENTRE_TOP
        assertEquals(40 * Match.EASE_ALPHA, firstStep, 1e-9)
        assertEquals(1 - kotlin.math.exp(-(1.0 / 240) / 0.030), Match.EASE_ALPHA, 1e-15)
        var steps = 1
        while (match.playerTop != match.playerTarget) {
            assertTrue(match.playerTarget - match.playerTop > Match.SNAP_DISTANCE)
            match.step()
            steps++
        }
        assertTrue(steps in 20..40, "$steps steps to settle")
    }

    @Test
    fun `detents past an end are discarded`() {
        val match = newMatch()
        match.turnSlowly(-1)
        repeat(30) { match.turnSlowly(-1) }
        match.step()
        assertEquals(0.0, match.playerTarget)
        match.turnSlowly(1)
        match.step()
        assertEquals(40.0, match.playerTarget)
        repeat(40) { match.turnSlowly(1) }
        match.step()
        assertEquals(Paddle.MAX_TOP, match.playerTarget)
    }

    @Test
    fun `queued detents apply in order at the next step, none dropped`() {
        val match = newMatch()
        match.turnSlowly(-1)
        match.turnSlowly(-1)
        match.turnSlowly(1)
        assertEquals(Paddle.CENTRE_TOP, match.playerTarget)
        match.step()
        assertEquals(Paddle.CENTRE_TOP - 40, match.playerTarget)
    }

    @Test
    fun `a drag moves the target 1 to 1, clamped`() {
        val match = newMatch()
        match.drag(-100.5)
        match.step()
        assertEquals(Paddle.CENTRE_TOP - 100.5, match.playerTarget)
        match.drag(-10_000.0)
        match.drag(3.0)
        match.step()
        assertEquals(3.0, match.playerTarget)
    }

    @Test
    fun `while paused nothing moves and inputs are not queued`() {
        val match = newMatch()
        match.serve()
        match.steps(20)
        match.pause()
        val frozen = match.snapshot()
        match.turnSlowly(5)
        match.drag(50.0)
        match.serve()
        assertEquals(emptyList(), match.advance(nanos(0.04)))
        assertEquals(emptyList(), match.step())
        assertEquals(frozen, match.snapshot())
    }

    @Test
    fun `moves queued before a pause are dropped, not applied on resume`() {
        val match = newMatch()
        match.turnSlowly(2)
        match.drag(30.0)
        match.pause()
        match.resume()
        match.step()
        assertEquals(Paddle.CENTRE_TOP, match.playerTarget)
    }

    @Test
    fun `serves come from the serve stream whatever the difficulty`() {
        val seed = 99L
        val launches = Difficulty.entries.map { difficulty ->
            val match = newMatch(seed, difficulty)
            val launched = mutableListOf<Ball>()
            match.stepUntil {
                if (phase is Phase.PlayerServe) serve()
                if (phase == Phase.Rally && ball.cx == Field.WIDTH / 2.0 && ball.cy == Field.HEIGHT / 2.0 && launched.lastOrNull() !== ball) launched += ball
                launched.size >= 8
            }
            launched
        }
        val serveStream = Random(Random(seed).nextLong())
        val expected = launches[0].map { Ball.served(if (it.vx > 0) Side.COMPUTER else Side.PLAYER, serveStream) }
        assertTrue(launches[0].any { it.vx < 0 } && launches[0].any { it.vx > 0 }, "both sides served")
        assertEquals(expected, launches[0])
        assertEquals(launches[0], launches[1])
        assertEquals(launches[0], launches[2])
    }

    @Test
    fun `keep-screen-on follows the phase`() {
        val match = newMatch()
        assertTrue(match.keepScreenOn)
        match.serve()
        assertTrue(match.keepScreenOn)
        match.pause()
        assertFalse(match.keepScreenOn)
        match.resume()
        assertTrue(match.keepScreenOn)
        assertNotNull(match.visibleBall)
    }

    @Test
    fun `two simple bots play a full match to 11`() {
        val match = newMatch(seed = 2026)
        val steps = playOut(match, PlayerBot(Random(9)))
        val over = assertIs<Phase.MatchOver>(match.phase)
        val scores = mapOf(Side.PLAYER to match.playerScore, Side.COMPUTER to match.computerScore)
        assertEquals(11, scores.getValue(over.winner))
        assertTrue(scores.values.min() < 11)
        println("bot match seed 2026: ${match.playerScore}-${match.computerScore} in $steps steps")
    }
}
