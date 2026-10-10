package com.yarosz.pong.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One recorded Match. Any change to the physics, the Computer, the serves, the easing or the wheel's
 * step moves this state; when the change is intended, re-record the expected value from the failure
 * message. Re-recorded for D31: the bot spins at 30 Detents a second, which earned 80 px a Detent on the
 * first curve (8 to 25 a second) and earns 66.7 px on the amended one (10 to 40 a second).
 */
class GoldenReplayTest {

    @Test
    fun `a fixed seed and input script end in the recorded state`() {
        val match = Match(20_261_010, Difficulty.NORMAL)
        val bot = PlayerBot(Random(5), reactionSteps = 24, misjudgement = Paddle.LENGTH / 4)
        repeat(30_000) { i ->
            if (i % 4_000 == 3_999) {
                match.pause()
                match.resume()
            }
            if (i % 7 == 0) match.drag(if (i % 2 == 0) 25.0 else -25.0)
            bot.act(match)
            match.step()
        }
        assertEquals(EXPECTED, match.snapshot())
    }

    private companion object {
        val EXPECTED = Snapshot(
            phase = Phase.Rally,
            phaseSteps = 0,
            paused = false,
            playerScore = 7,
            computerScore = 0,
            ball = Ball(840.5378645799868, 1069.1225458982776, -738.5490216000001, 162.01712913587912),
            playerTarget = 709.3808231569426,
            playerTop = 633.6331482736321,
            computerTop = 864.4052083550051,
        )
    }
}
