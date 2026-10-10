package com.yarosz.pong.core

import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/** The computer's paddle and the controller that moves it. */
class Computer internal constructor(val difficulty: Difficulty, private val rng: Random) {

    var top = Paddle.CENTRE_TOP
        private set

    /** One approach of the ball toward the computer: its reaction countdown and where on the paddle it aims. */
    private class Approach(val aim: Double) {
        var waitedSteps = 0
    }

    private var approach: Approach? = null

    /**
     * Moves the paddle one step. [ball] is null when no ball is in play (serve waits, point pauses); a
     * ball moving toward the computer starts an approach, which re-rolls the aim.
     */
    internal fun step(ball: Ball?) {
        val incoming = ball != null && ball.vx > 0
        if (!incoming) approach = null else if (approach == null) approach = Approach(rollAim())
        val current = approach
        if (ball != null && current != null && current.waitedSteps >= difficulty.reactionSteps) {
            moveToward(Paddle.clampTop(intercept(ball) + current.aim - Paddle.HALF_LENGTH), difficulty.maxSpeed)
        } else {
            moveToward(Paddle.CENTRE_TOP, difficulty.maxSpeed / 2)
        }
        current?.let { it.waitedSteps++ }
    }

    internal fun intercept(ball: Ball): Double =
        if (difficulty.predictsIntercept) interceptY(ball, Paddle.face(Side.COMPUTER) - Ball.HALF) else ball.cy

    private fun rollAim(): Double {
        val offset = rng.nextDouble(-1.0, 1.0) * difficulty.contactOffset * Paddle.HALF_LENGTH
        val error = rng.nextDouble(-1.0, 1.0) * difficulty.aimError * Paddle.LENGTH
        return offset + error
    }

    private fun moveToward(goal: Double, speed: Double) {
        val reach = speed / Match.STEPS_PER_SECOND
        val gap = goal - top
        top = if (abs(gap) <= reach) goal else top + gap.sign * reach
    }
}
