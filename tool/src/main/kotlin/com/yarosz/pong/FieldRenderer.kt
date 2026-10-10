package com.yarosz.pong

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.yarosz.pong.core.Ball
import com.yarosz.pong.core.Field
import com.yarosz.pong.core.Match
import com.yarosz.pong.core.Paddle
import com.yarosz.pong.screen.FieldLayout
import com.yarosz.pong.screen.Letterbox

private val PLAYER_DIGITS = Array(Match.WINNING_SCORE + 1) { FieldLayout.scoreRects(it, FieldLayout.PLAYER_SCORE_X) }
private val COMPUTER_DIGITS = Array(Match.WINNING_SCORE + 1) { FieldLayout.scoreRects(it, FieldLayout.COMPUTER_SCORE_X) }

/** DESIGN.md items 1-7, in draw order: digits, centre line, pause bars, paddles, ball. Paddles and ball are the Match's drawn (blended) positions. */
fun DrawScope.drawField(match: Match, pauseBars: Boolean) {
    val box = Letterbox(size.width, size.height)
    withTransform({
        translate(box.left, box.top)
        scale(box.scale, box.scale, pivot = Offset.Zero)
    }) {
        PLAYER_DIGITS[match.playerScore].forEach(::rect)
        COMPUTER_DIGITS[match.computerScore].forEach(::rect)
        centreLine()
        if (pauseBars) pauseBars()
        paddle(Paddle.PLAYER_LEFT, match.drawnPlayerTop)
        paddle(Paddle.COMPUTER_LEFT, match.drawnComputerTop)
        match.drawnBall?.let { ball(it) }
    }
}

private fun DrawScope.rect(r: FloatArray) = rect(r[0], r[1], r[2], r[3])

private fun DrawScope.rect(left: Float, top: Float, right: Float, bottom: Float, color: Color = Color.White) =
    drawRect(color, Offset(left, top), Size(right - left, bottom - top))

private fun DrawScope.centreLine() {
    val left = FieldLayout.CENTRE_X - FieldLayout.LINE_WIDTH / 2
    var y = FieldLayout.LINE_TOP
    while (y < Field.HEIGHT) {
        rect(left, y, left + FieldLayout.LINE_WIDTH, minOf(y + FieldLayout.DASH, Field.HEIGHT.toFloat()))
        y += FieldLayout.DASH + FieldLayout.DASH_GAP
    }
}

private fun DrawScope.pauseBars() {
    val leftBar = FieldLayout.CENTRE_X - FieldLayout.BAR_GAP / 2 - FieldLayout.BAR_WIDTH
    val rightBar = FieldLayout.CENTRE_X + FieldLayout.BAR_GAP / 2
    rect(leftBar, FieldLayout.BAR_TOP, leftBar + FieldLayout.BAR_WIDTH, FieldLayout.BAR_BOTTOM)
    rect(rightBar, FieldLayout.BAR_TOP, rightBar + FieldLayout.BAR_WIDTH, FieldLayout.BAR_BOTTOM)
}

private fun DrawScope.paddle(left: Double, top: Double) =
    rect(left.toFloat(), top.toFloat(), (left + Paddle.THICKNESS).toFloat(), (top + Paddle.LENGTH).toFloat())

private fun DrawScope.ball(ball: Ball) {
    val cx = ball.cx.toFloat()
    val cy = ball.cy.toFloat()
    val halo = FieldLayout.HALO / 2
    val half = Ball.HALF.toFloat()
    rect(cx - halo, cy - halo, cx + halo, cy + halo, Color.Black)
    rect(cx - half, cy - half, cx + half, cy + half)
}
