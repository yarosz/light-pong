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
}
