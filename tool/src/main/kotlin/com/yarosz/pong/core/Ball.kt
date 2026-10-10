package com.yarosz.pong.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/** The ball: its centre in field pixels and its velocity in px/s. */
data class Ball(val cx: Double, val cy: Double, val vx: Double, val vy: Double) {

    val left get() = cx - HALF
    val top get() = cy - HALF
    val right get() = cx + HALF
    val bottom get() = cy + HALF

    /** The ball after a hit by [side]'s paddle at offset [o] in [-1, 1] from the paddle's centre. */
    fun bouncedOff(side: Side, o: Double): Ball {
        val speed = min(abs(vx) * SPEED_FACTOR, MAX_SPEED)
        val angle = MAX_BOUNCE * o.coerceIn(-1.0, 1.0)
        val direction = if (side == Side.PLAYER) 1.0 else -1.0
        return copy(vx = direction * speed, vy = speed * StrictMath.tan(angle))
    }

    companion object {
        const val SIZE = 32.0
        const val HALF = SIZE / 2
        const val SERVE_SPEED = 585.0
        const val MAX_SPEED = 2 * SERVE_SPEED
        const val SPEED_FACTOR = 1.06
        const val MAX_BOUNCE = 50 * PI / 180
        private const val MIN_SERVE_ANGLE = 10 * PI / 180
        private const val MAX_SERVE_ANGLE = 30 * PI / 180

        val AT_CENTRE = Ball(Field.WIDTH / 2.0, Field.HEIGHT / 2.0, 0.0, 0.0)

        fun served(toward: Side, rng: Random): Ball {
            val angle = MIN_SERVE_ANGLE + (MAX_SERVE_ANGLE - MIN_SERVE_ANGLE) * rng.nextDouble()
            val sign = if (rng.nextBoolean()) 1.0 else -1.0
            val vx = if (toward == Side.COMPUTER) SERVE_SPEED else -SERVE_SPEED
            return AT_CENTRE.copy(vx = vx, vy = sign * SERVE_SPEED * StrictMath.tan(angle))
        }
    }
}
