package com.yarosz.pong.screen

import kotlin.math.roundToInt

/**
 * Frame intervals during a Rally, batched [BATCH] to a line (`frame_ms=16.7 frame_ms=16.6 ...`) for
 * lp3-measure. The buffer is reused; only the finished line allocates.
 */
class FrameLog {
    private val tenths = IntArray(BATCH)
    private var count = 0

    /** Records one interval; returns a line when [BATCH] have been recorded. */
    fun record(intervalNanos: Long): String? {
        tenths[count++] = (intervalNanos / 100_000.0).roundToInt()
        if (count < BATCH) return null
        count = 0
        return buildString(BATCH * 15) {
            for (i in 0 until BATCH) {
                if (i > 0) append(' ')
                append("frame_ms=").append(tenths[i] / 10).append('.').append(tenths[i] % 10)
            }
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
