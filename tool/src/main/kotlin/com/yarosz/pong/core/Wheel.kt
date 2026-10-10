package com.yarosz.pong.core

/**
 * How far a Detent moves the Paddle target (DESIGN.md item 20, decision log D31). The step rises
 * linearly with the Detent rate, from [SLOW_STEP] at [SLOW_RATE] Detents a second or slower to
 * [FAST_STEP] at [FAST_RATE] or faster, and [factor] scales the whole curve.
 */
enum class Sensitivity(val factor: Double) {
    NORMAL(1.0),
    FAST(1.5),
    ;

    fun step(rate: Double): Double {
        val ramp = ((rate - SLOW_RATE) / (FAST_RATE - SLOW_RATE)).coerceIn(0.0, 1.0)
        return (SLOW_STEP + (FAST_STEP - SLOW_STEP) * ramp) * factor
    }

    companion object {
        val DEFAULT = NORMAL
        const val SLOW_STEP = 40.0
        const val FAST_STEP = 80.0
        const val SLOW_RATE = 8.0
        const val FAST_RATE = 25.0
    }
}

/**
 * The wheel's Detent rate: Detents a second over the Detents of the last [WINDOW_MILLIS], this one
 * included, measured from their event times: n Detents spanning t ms read (n - 1) / t. A lone Detent,
 * more than 100 ms after the last, reads 0. Detents in the same millisecond count as 1 ms apart, and
 * an event time earlier than the last starts the window afresh.
 */
class DetentRate {
    private val recent = ArrayDeque<Long>()

    /** Records a Detent at [atMillis] (uptime ms) and returns the rate it makes. */
    fun detent(atMillis: Long): Double {
        if (recent.isNotEmpty() && atMillis < recent.last()) recent.clear()
        while (recent.isNotEmpty() && recent.first() < atMillis - WINDOW_MILLIS) recent.removeFirst()
        recent.addLast(atMillis)
        if (recent.size == 1) return 0.0
        val span = (atMillis - recent.first()).coerceAtLeast(1)
        return (recent.size - 1) * 1_000.0 / span
    }

    companion object {
        const val WINDOW_MILLIS = 100L
    }
}
