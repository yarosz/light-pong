package com.yarosz.pong.core

/**
 * How well the Computer plays. [aimError] and [contactOffset] are fractions: the error is uniform within
 * ±aimError paddle lengths, the intended contact offset within ±contactOffset half-lengths.
 */
enum class Difficulty(
    val reactionMillis: Int,
    val maxSpeed: Double,
    val predictsIntercept: Boolean,
    val aimError: Double,
    val contactOffset: Double,
) {
    EASY(250, 322.0, predictsIntercept = false, aimError = 0.35, contactOffset = 0.3),
    NORMAL(150, 468.0, predictsIntercept = true, aimError = 0.2, contactOffset = 0.6),
    HARD(80, 614.0, predictsIntercept = true, aimError = 0.08, contactOffset = 0.85),
    ;

    /** The reaction delay rounded up to whole simulation steps. */
    val reactionSteps get() = (reactionMillis * Match.STEPS_PER_SECOND + 999) / 1000

    companion object {
        val DEFAULT = NORMAL
    }
}
