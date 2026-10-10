package com.yarosz.pong.core

/** The simulation runs in fixed Steps of 1/240 s. */
const val STEPS_PER_SECOND = 240
const val STEP_SECONDS = 1.0 / STEPS_PER_SECOND

/** The playing field, in pixels: the LP3's app area at 480 dpi. y grows downward; the walls are y 0 and HEIGHT. */
object Field {
    const val WIDTH = 1080
    const val HEIGHT = 1168
}

/** Paddle geometry shared by both sides. A paddle's position is its top y, in [0, MAX_TOP]. */
object Paddle {
    const val LENGTH = 192.0
    const val THICKNESS = 24.0
    const val HALF_LENGTH = LENGTH / 2
    const val MAX_TOP = Field.HEIGHT - LENGTH
    const val CENTRE_TOP = MAX_TOP / 2

    const val PLAYER_LEFT = 48.0
    const val COMPUTER_LEFT = Field.WIDTH - PLAYER_LEFT - THICKNESS

    fun left(side: Side) = if (side == Side.PLAYER) PLAYER_LEFT else COMPUTER_LEFT

    /** The x of the face the ball strikes. */
    fun face(side: Side) = if (side == Side.PLAYER) PLAYER_LEFT + THICKNESS else COMPUTER_LEFT

    fun clampTop(top: Double) = top.coerceIn(0.0, MAX_TOP)
}
