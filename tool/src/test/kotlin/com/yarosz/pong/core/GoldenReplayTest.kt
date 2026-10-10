package com.yarosz.pong.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One recorded Match. Any change to the physics, the Computer, the serves or the easing moves this
 * state; when the change is intended, re-record the expected value from the failure message.
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
            playerScore = 4,
            computerScore = 1,
            ball = Ball(959.8067928529548, 867.4153372579815, 879.6237015099458, 424.8177370384114),
            playerTarget = 471.0,
            playerTop = 480.05776970951786,
            computerTop = 817.3789582853769,
        )
    }
}
