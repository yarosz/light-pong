package com.yarosz.pong.screen

import kotlin.math.roundToInt

/**
 * The wheel's bursts during play, for tuning the step curve against real hands (decision log D22,
 * D31). A burst is one spin of the wheel: a run of Detents with no gap of [QUIET_MILLIS]. When it ends
 * it makes one PongEvent line, `spin detents=12 ms=410 peak_rate=31`: its Detents, the ms from its
 * first to its last, and the fastest Detent rate the Match applied among them in Detents a second,
 * rounded. The key names are frozen (AGENTS.md).
 */
class BurstLog {
    private var detents = 0
    private var firstMillis = 0L
    private var lastMillis = 0L
    private var peakRate = 0.0

    /**
     * Records a Detent at [atMillis] that the Match moved by [rate] ([com.yarosz.pong.core.Match.turn]);
     * returns the last burst's line when this one starts a new burst.
     */
    fun detent(atMillis: Long, rate: Double): String? {
        val ended = quiet(atMillis)
        if (detents == 0) firstMillis = atMillis
        detents++
        lastMillis = atMillis
        peakRate = maxOf(peakRate, rate)
        return ended
    }

    /** The burst's line once the wheel has been quiet for [QUIET_MILLIS] at [nowMillis]. */
    fun quiet(nowMillis: Long): String? = if (detents > 0 && nowMillis - lastMillis >= QUIET_MILLIS) end() else null

    /** The burst's line now, when play stops mid-burst. */
    fun end(): String? {
        if (detents == 0) return null
        val line = "spin detents=$detents ms=${lastMillis - firstMillis} peak_rate=${peakRate.roundToInt()}"
        detents = 0
        peakRate = 0.0
        return line
    }

    companion object {
        const val QUIET_MILLIS = 150L
    }
}
