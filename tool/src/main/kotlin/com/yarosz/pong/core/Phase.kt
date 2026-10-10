package com.yarosz.pong.core

enum class Side { PLAYER, COMPUTER }

enum class SoundEvent { PADDLE, WALL, POINT }

/**
 * Where a Match is between serve and Match over. Paused is not a Phase: it freezes whichever Phase it
 * interrupted. The Match counts whole Steps spent in the current Phase, so every timing is exact.
 */
sealed interface Phase {
    /** The player's serve is pending; 60 s without paddle input pauses the Match. */
    data object PlayerServe : Phase

    /** The computer's serve is pending; it launches after [Match.COMPUTER_SERVE_STEPS]. */
    data object ComputerServe : Phase

    data object Rally : Phase

    /** The ball crossed a goal line and is hidden; the next serve (or Match over) follows. */
    data object PointPause : Phase

    /** The rally resumes after a pause: the ball is frozen and visible, the paddles move. */
    data object Hold : Phase

    data class MatchOver(val winner: Side) : Phase
}
