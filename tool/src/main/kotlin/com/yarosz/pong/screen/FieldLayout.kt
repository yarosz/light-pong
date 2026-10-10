package com.yarosz.pong.screen

import com.yarosz.pong.core.Field
import kotlin.math.abs

/** Where the renderer draws the score, the centre line and the pause control (DESIGN.md items 4-6), in field px. */
object FieldLayout {
    const val CENTRE_X = Field.WIDTH / 2f

    const val DIGIT_WIDTH = 72f
    const val DIGIT_HEIGHT = 120f
    const val DIGIT_STROKE = 16f
    const val DIGIT_GAP = 24f
    const val DIGIT_TOP = 48f
    const val PLAYER_SCORE_X = 270f
    const val COMPUTER_SCORE_X = 810f

    const val LINE_WIDTH = 8f
    const val LINE_TOP = 168f
    const val DASH = 32f
    const val DASH_GAP = 32f

    const val BAR_WIDTH = 16f
    const val BAR_GAP = 16f
    const val BAR_TOP = 56f
    const val BAR_BOTTOM = 120f

    const val HALO = 40f

    const val PAUSE_TARGET_Y = 88f
    const val PAUSE_TARGET_HALF = 72f

    fun inPauseTarget(x: Double, y: Double) = abs(x - CENTRE_X) <= PAUSE_TARGET_HALF && abs(y - PAUSE_TARGET_Y) <= PAUSE_TARGET_HALF

    /**
     * The seven segments a, b, c, d, e, f, g of a [DIGIT_WIDTH] x [DIGIT_HEIGHT] digit as
     * (left, top, right, bottom) relative to its top-left corner.
     */
    private val SEGMENTS: List<FloatArray> = run {
        val w = DIGIT_WIDTH
        val h = DIGIT_HEIGHT
        val s = DIGIT_STROKE
        val midTop = (h - s) / 2
        listOf(
            floatArrayOf(0f, 0f, w, s),
            floatArrayOf(w - s, 0f, w, midTop + s),
            floatArrayOf(w - s, midTop, w, h),
            floatArrayOf(0f, h - s, w, h),
            floatArrayOf(0f, midTop, s, h),
            floatArrayOf(0f, 0f, s, midTop + s),
            floatArrayOf(0f, midTop, w, midTop + s),
        )
    }

    private val LIT = listOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")

    /** The rects, in field px, that draw [score] centred on [centreX]. */
    fun scoreRects(score: Int, centreX: Float): List<FloatArray> {
        val digits = score.toString()
        val width = digits.length * DIGIT_WIDTH + (digits.length - 1) * DIGIT_GAP
        val left = centreX - width / 2
        return digits.flatMapIndexed { i, digit ->
            val x = left + i * (DIGIT_WIDTH + DIGIT_GAP)
            LIT[digit - '0'].map { segment ->
                val r = SEGMENTS[segment - 'a']
                floatArrayOf(x + r[0], DIGIT_TOP + r[1], x + r[2], DIGIT_TOP + r[3])
            }
        }
    }
}
