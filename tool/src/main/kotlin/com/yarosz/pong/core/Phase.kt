package com.yarosz.pong.core

enum class Side { PLAYER, COMPUTER }

enum class SoundEvent { PADDLE, WALL, POINT }

/**
 * Where a Match is between serve and Match over. Timers count whole simulation steps (240 per second),
 * so every timing is exact. Paused is not a Phase: it freezes whichever Phase it interrupted.
 */
sealed interface Phase {
    /** The player's serve is pending; [idleSteps] counts steps since the last paddle input. */
    data class PlayerServe(val idleSteps: Int = 0) : Phase

    /** The computer's serve is pending; it launches after [Match.COMPUTER_SERVE_STEPS]. */
    data class ComputerServe(val steps: Int = 0) : Phase

    data object Rally : Phase

    /** The ball crossed a goal line and is hidden; the next serve (or Match over) follows. */
    data class PointPause(val steps: Int = 0) : Phase

    /** The rally resumes after a pause: the ball is frozen and visible, the paddles move. */
    data class Hold(val steps: Int = 0) : Phase

    data class MatchOver(val winner: Side) : Phase
}
