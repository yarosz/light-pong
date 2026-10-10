package com.yarosz.pong.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RallyTest {

    private val step = Match.STEP_SECONDS
    private val far = Paddle.MAX_TOP

    private fun degrees(ball: Ball) = atan(ball.vy / ball.vx) * 180 / PI

    private fun untilHit(start: Ball, playerTop: Double, computerTop: Double): Pair<Ball, List<SoundEvent>> {
        var ball = start
        repeat(2_000) {
            val result = rallyStep(ball, playerTop, computerTop, step)
            ball = result.ball
            if (SoundEvent.PADDLE in result.sounds) return ball to result.sounds
            assertNull(result.scorer)
        }
        error("no paddle hit")
    }

    @Test
    fun `a centre hit on the player's face returns the ball flat and 6 percent faster`() {
        val top = 400.0
        val start = Ball(300.0, top + Paddle.HALF_LENGTH, -Ball.SERVE_SPEED, 0.0)
        val (ball, _) = untilHit(start, top, far)
        assertEquals(Ball.SERVE_SPEED * 1.06, ball.vx, 1e-9)
        assertEquals(0.0, ball.vy, 1e-9)
        assertEquals(PLAYER_CONTACT_X, ball.cx, 3.0)
    }

    @Test
    fun `the bounce angle is 50 degrees times the offset`() {
        val top = 400.0
        val halfReach = Paddle.HALF_LENGTH + Ball.HALF
        val start = Ball(300.0, top + Paddle.HALF_LENGTH + halfReach / 2, -Ball.SERVE_SPEED, 0.0)
        val (ball, _) = untilHit(start, top, far)
        assertEquals(25.0, degrees(ball), 1e-6)
    }

    @Test
    fun `an end-cap hit counts as plus or minus one`() {
        val top = 500.0
        val start = Ball(64.0, 470.0, -Ball.SERVE_SPEED, 900.0)
        val (ball, _) = untilHit(start, top, far)
        assertTrue(ball.vx > 0)
        assertEquals(-50.0, degrees(ball), 1e-6)
        assertEquals(top - Ball.HALF, ball.cy, 3.0)
    }

    @Test
    fun `a paddle moving onto a ball behind its face strikes it with the end cap`() {
        val ball = Ball(60.0, 300.0, -Ball.SERVE_SPEED, 0.0)
        val result = rallyStep(ball, playerTop = 290.0, computerTop = far, seconds = step)
        assertEquals(listOf(SoundEvent.PADDLE), result.sounds)
        assertTrue(result.ball.vx > 0)
        assertEquals(-50.0, degrees(result.ball), 1e-6)
    }

    @Test
    fun `a squeezed ball is set back in front of the face`() {
        val ball = Ball(60.0, 20.0, -Ball.SERVE_SPEED, 0.0)
        val result = rallyStep(ball, playerTop = 10.0, computerTop = far, seconds = step)
        assertEquals(listOf(SoundEvent.PADDLE), result.sounds)
        assertTrue(result.ball.left >= Paddle.face(Side.PLAYER))
    }

    @Test
    fun `the speed caps at twice the serve speed`() {
        val top = 400.0
        val start = Ball(300.0, top + Paddle.HALF_LENGTH, -1150.0, 0.0)
        val (ball, _) = untilHit(start, top, far)
        assertEquals(Ball.MAX_SPEED, ball.vx, 1e-9)
    }

    @Test
    fun `the computer's paddle sends the ball left`() {
        val top = 400.0
        val start = Ball(800.0, top + Paddle.HALF_LENGTH, Ball.SERVE_SPEED, 0.0)
        val (ball, _) = untilHit(start, far, top)
        assertTrue(ball.vx < 0)
    }

    @Test
    fun `walls flip vy and play the wall sound`() {
        val start = Ball(540.0, Ball.HALF + 1, 100.0, -1000.0)
        val result = rallyStep(start, far, far, step)
        assertEquals(listOf(SoundEvent.WALL), result.sounds)
        assertTrue(result.ball.vy > 0)
        assertTrue(result.ball.top >= 0)
    }

    @Test
    fun `a ball reaching a goal line scores for the other side`() {
        assertEquals(Side.COMPUTER, rallyStep(Ball(17.0, 100.0, -585.0, 0.0), far, far, step).scorer)
        assertEquals(Side.PLAYER, rallyStep(Ball(1063.0, 100.0, 585.0, 0.0), far, 0.0, step).scorer)
    }

    @Test
    fun `a fast ball cannot tunnel through a paddle in one long sweep`() {
        val top = 400.0
        val start = Ball(300.0, top + Paddle.HALF_LENGTH, -1170.0, 0.0)
        val result = rallyStep(start, top, far, seconds = 0.3)
        assertTrue(result.ball.vx > 0)
        assertNull(result.scorer)
    }

    @Test
    fun `the predicted intercept folds the path at the walls`() {
        val span = Field.HEIGHT - Ball.SIZE
        val ball = Ball(540.0, Ball.HALF, 500.0, 500.0 * (span + 100) / 452)
        assertEquals(Field.HEIGHT - Ball.HALF - 100, interceptY(ball, 992.0), 1e-6)
        val straight = Ball(540.0, 600.0, 500.0, 0.0)
        assertEquals(600.0, interceptY(straight, 992.0), 1e-9)
        assertEquals(600.0, interceptY(straight.copy(vx = -500.0), 992.0), 1e-9)
    }
}
