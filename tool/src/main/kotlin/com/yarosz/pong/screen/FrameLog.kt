package com.yarosz.pong.screen

import kotlin.math.roundToInt

/**
 * Frames during a Rally, batched [BATCH] to a PongPerf line for lp3-measure's p90.py. Each frame adds
 * `frame_ms=16.6 steps=4 draw_ms=0.42`; the line ends with the Ball's `speed=1170` (|vx| in px/s) at
 * its last frame. The key names are frozen (AGENTS.md). The buffers are reused; only the finished line
 * allocates.
 */
class FrameLog {
    private val frameTenths = IntArray(BATCH)
    private val steps = IntArray(BATCH)
    private val drawHundredths = IntArray(BATCH)
    private var count = 0

    /**
     * Records one frame: its interval, the Steps it ran, and the time the field's last draw took. Returns
     * a line when [BATCH] have been recorded.
     */
    fun record(intervalNanos: Long, stepsRun: Int, drawNanos: Long, speed: Double): String? {
        frameTenths[count] = (intervalNanos / 100_000.0).roundToInt()
        steps[count] = stepsRun
        drawHundredths[count] = (drawNanos / 10_000.0).roundToInt()
        if (++count < BATCH) return null
        count = 0
        return buildString(BATCH * 36 + 12) {
            for (i in 0 until BATCH) {
                append("frame_ms=").append(frameTenths[i] / 10).append('.').append(frameTenths[i] % 10)
                append(" steps=").append(steps[i])
                append(" draw_ms=").append(drawHundredths[i] / 100).append('.')
                val hundredths = drawHundredths[i] % 100
                if (hundredths < 10) append('0')
                append(hundredths).append(' ')
            }
            append("speed=").append(speed.roundToInt())
        }
    }

    /** Drops a partial batch, when the Rally ends. */
    fun reset() {
        count = 0
    }

    companion object {
        const val BATCH = 60
    }
}
