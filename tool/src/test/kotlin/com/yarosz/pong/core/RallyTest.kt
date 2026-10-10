package com.yarosz.pong.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RallyTest {

    private val step = STEP_SECONDS
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
        val start = Ball(74.0, 470.0, -Ball.SERVE_SPEED, 900.0)
        val (ball, _) = untilHit(start, top, far)
        assertTrue(ball.vx > 0)
        assertEquals(-50.0, degrees(ball), 1e-6)
        assertEquals(top - Ball.HALF, ball.cy, 3.0)
    }

    @Test
    fun `a paddle moving onto a ball beside its face strikes it as an end cap, from in front of the face`() {
        val ball = Ball(60.0, 300.0, -Ball.SERVE_SPEED, 0.0)
        val result = rallyStep(ball, playerTop = 290.0, computerTop = far, seconds = step)
        assertEquals(listOf(SoundEvent.PADDLE), result.sounds)
        assertTrue(result.ball.vx > 0)
        assertEquals(-50.0, degrees(result.ball), 1e-6)
        assertEquals(300.0 + result.ball.vy * step, result.ball.cy, 1e-9)
        assertEquals(PLAYER_CONTACT_X + result.ball.vx * step, result.ball.cx, 1e-9)
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

    /** Steps [start] against a player paddle whose top follows [tops], one per step. */
    private fun run(start: Ball, tops: List<Double>): Pair<Ball, Int> {
        var ball = start
        var hits = 0
        for (top in tops) {
            val result = rallyStep(ball, top, far, step)
            ball = result.ball
            hits += result.sounds.count { it == SoundEvent.PADDLE }
        }
        return ball to hits
    }

    private fun eased(from: Double, to: Double, steps: Int): List<Double> {
        var top = from
        return List(steps) {
            top += (to - top) * Match.EASE_ALPHA
            if (kotlin.math.abs(to - top) <= Match.SNAP_DISTANCE) top = to
            top
        }
    }

    private val probe = Ball(86.0, 460.0, -Ball.SERVE_SPEED, 117.0)

    @Test
    fun `a paddle easing onto a ball it just struck hits it once`() {
        for (detents in 1..6) {
            val (ball, hits) = run(probe, eased(500.0, 500.0 - detents * Match.DETENT, 60))
            assertEquals(1, hits, "$detents detents")
            assertEquals(Ball.SERVE_SPEED * 1.06, ball.vx, 1e-9, "$detents detents")
        }
    }

    @Test
    fun `a paddle chasing the ball it just struck hits it once`() {
        val (ball, hits) = run(probe, List(40) { 500.0 - 15.0 * (it + 1) })
        assertEquals(1, hits)
        assertEquals(Ball.SERVE_SPEED * 1.06, ball.vx, 1e-9)
    }

    @Test
    fun `a paddle moving onto a ball leaving it only sets it in front of the face`() {
        val leaving = Ball(70.0, 300.0, 620.0, -100.0)
        val result = rallyStep(leaving, playerTop = 250.0, computerTop = far, seconds = step)
        assertEquals(emptyList(), result.sounds)
        assertEquals(leaving.vx, result.ball.vx)
        assertEquals(leaving.vy, result.ball.vy)
        assertTrue(result.ball.left >= Paddle.face(Side.PLAYER))
    }

    @Test
    fun `a ball wedged between a wall and the cap of the paddle it left is not sped up`() {
        var ball = Ball(80.0, 16.25, 782.9, 900.0)
        repeat(10) {
            val result = rallyStep(ball, playerTop = 32.5, computerTop = far, seconds = step)
            assertTrue(SoundEvent.PADDLE !in result.sounds, "step $it: ${result.sounds}")
            assertEquals(782.9, result.ball.vx)
            assertTrue(result.ball.cx > ball.cx, "step $it: the ball moves on")
            ball = result.ball
        }
    }

    @Test
    fun `a paddle parked against a wall cannot pin the ball`() {
        val (ball, hits) = run(Ball(60.0, 17.0, -Ball.SERVE_SPEED, 0.0), List(20) { 32.0 })
        assertEquals(1, hits)
        assertTrue(ball.cx > 100, "ball at ${ball.cx}")
    }

    @Test
    fun `a ball wholly past the face is not saved by a paddle moving onto it`() {
        var ball = Ball(51.9, 444.6, -Ball.MAX_SPEED, 0.0)
        repeat(20) {
            val result = rallyStep(ball, playerTop = 344.0, computerTop = far, seconds = step)
            assertTrue(SoundEvent.PADDLE !in result.sounds)
            assertEquals(444.6, result.ball.cy)
            assertEquals(-Ball.MAX_SPEED, result.ball.vx)
            ball = result.ball
            if (result.scorer != null) {
                assertEquals(Side.COMPUTER, result.scorer)
                return
            }
        }
        error("the ball never reached the goal line")
    }

    @Test
    fun `a ball wholly past the face glances off an end cap without a hit`() {
        var ball = Ball(50.0, 290.0, -Ball.SERVE_SPEED, 900.0)
        repeat(20) {
            val result = rallyStep(ball, playerTop = 330.0, computerTop = far, seconds = step)
            assertTrue(SoundEvent.PADDLE !in result.sounds)
            assertEquals(-Ball.SERVE_SPEED, result.ball.vx)
            assertTrue(result.ball.bottom <= 330.0 + 1e-9)
            ball = result.ball
            if (result.scorer != null) return
        }
        error("the ball never reached the goal line")
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
