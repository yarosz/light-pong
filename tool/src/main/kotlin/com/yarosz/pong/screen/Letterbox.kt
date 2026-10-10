package com.yarosz.pong.screen

import com.yarosz.pong.core.Field
import kotlin.math.min

/** The field scaled uniformly into a [width] x [height] canvas and centred in it (DESIGN.md item 1). */
class Letterbox(width: Float, height: Float) {
    val scale = min(width / Field.WIDTH, height / Field.HEIGHT)
    val left = (width - Field.WIDTH * scale) / 2
    val top = (height - Field.HEIGHT * scale) / 2

    fun fieldX(x: Float) = ((x - left) / scale).toDouble()
    fun fieldY(y: Float) = ((y - top) / scale).toDouble()

    /** The pause control's 144x144 touch target (DESIGN.md item 6) in canvas px: left, top, right, bottom. */
    fun pauseTarget(): FloatArray {
        val half = FieldLayout.PAUSE_TARGET_HALF
        return floatArrayOf(
            left + (FieldLayout.CENTRE_X - half) * scale,
            top + (FieldLayout.PAUSE_TARGET_Y - half) * scale,
            left + (FieldLayout.CENTRE_X + half) * scale,
            top + (FieldLayout.PAUSE_TARGET_Y + half) * scale,
        )
    }
}
