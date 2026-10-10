package com.yarosz.pong.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One recorded Match. Any change to the physics, the Computer, the serves, the easing or the wheel's
 * step moves this state; when the change is intended, re-record the expected value from the failure
 * message. Re-recorded for D31: the bot spins at 30 Detents a second, which now earns 80 px a Detent.
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
            playerScore = 6,
            computerScore = 1,
            ball = Ball(388.79172150000636, 776.0477841309232, 696.74436, -46.14866588898475),
            playerTarget = 490.31991744066045,
            playerTop = 499.37831276288847,
            computerTop = 612.3786996209869,
        )
    }
}
