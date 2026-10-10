package com.yarosz.pong.core

import kotlin.math.max
import kotlin.math.min

/** One rally step's result: the moved ball, the sounds it made, and who scored if it crossed a goal line. */
internal class RallyStep(val ball: Ball, val sounds: List<SoundEvent>, val scorer: Side?)

/** A contact [time] seconds in and the ball just after it. A deflection that is not a hit has no [sound]. */
private class Contact(val time: Double, val ball: Ball, val sound: SoundEvent?)

// A ball wedged between a wall and an end cap bounces between them many times in one step; past this
// many contacts it slides straight on in x for the rest of the step, which carries it out of the wedge.
private const val MAX_CONTACTS = 16

/**
 * Moves [start] for [seconds] against the walls and both paddles, given each paddle's top after this
 * step's movement. Collisions are swept, so the ball cannot pass through a paddle at any speed. A paddle
 * hits the ball only while it moves toward that paddle and has not wholly passed its face, so one
 * approach gets one hit, one speed-up and one paddle sound (D23, D24).
 */
internal fun rallyStep(start: Ball, playerTop: Double, computerTop: Double, seconds: Double): RallyStep {
    val sounds = mutableListOf<SoundEvent>()
    var ball = start
    for (side in Side.entries) {
        val contact = pushedOut(ball, side, if (side == Side.PLAYER) playerTop else computerTop) ?: continue
        ball = contact.ball
        contact.sound?.let { sounds += it }
    }
    var remaining = seconds
    var contacts = 0
    while (true) {
        if (contacts == MAX_CONTACTS) {
            ball = ball.copy(cx = ball.cx + ball.vx * remaining)
            break
        }
        val contact = listOfNotNull(
            wallContact(ball),
            paddleContact(ball, Side.PLAYER, playerTop),
            paddleContact(ball, Side.COMPUTER, computerTop),
        ).minByOrNull { it.time }
        if (contact == null || contact.time > remaining) {
            ball = ball.copy(cx = ball.cx + ball.vx * remaining, cy = ball.cy + ball.vy * remaining)
            break
        }
        ball = contact.ball
        contact.sound?.let { sounds += it }
        remaining -= contact.time
        contacts++
    }
    val scorer = when {
        ball.left <= 0 -> Side.COMPUTER
        ball.right >= Field.WIDTH -> Side.PLAYER
        else -> null
    }
    return RallyStep(ball, sounds, scorer)
}

private fun wallContact(ball: Ball): Contact? = when {
    ball.vy < 0 -> {
        val time = max(0.0, ball.top / -ball.vy)
        Contact(time, ball.copy(cx = ball.cx + ball.vx * time, cy = Ball.HALF, vy = -ball.vy), SoundEvent.WALL)
    }
    ball.vy > 0 -> {
        val time = max(0.0, (Field.HEIGHT - ball.bottom) / ball.vy)
        Contact(time, ball.copy(cx = ball.cx + ball.vx * time, cy = Field.HEIGHT - Ball.HALF, vy = -ball.vy), SoundEvent.WALL)
    }
    else -> null
}

/** The ball's centre range, per axis, that touches the paddle: the paddle grown by half the ball. */
private class Reach(side: Side, paddleTop: Double) {
    val left = Paddle.left(side) - Ball.HALF
    val right = Paddle.left(side) + Paddle.THICKNESS + Ball.HALF
    val top = paddleTop - Ball.HALF
    val bottom = paddleTop + Paddle.LENGTH + Ball.HALF
    val centreY = paddleTop + Paddle.HALF_LENGTH

    /** The ball's centre x when it touches the face, and when it touches the back. */
    val front = if (side == Side.PLAYER) right else left
    val back = if (side == Side.PLAYER) left else right

    fun overlaps(ball: Ball) = ball.cx > left && ball.cx < right && ball.cy > top && ball.cy < bottom
}

private fun offset(ball: Ball, centreY: Double) = (ball.cy - centreY) / (Paddle.HALF_LENGTH + Ball.HALF)

private fun movingToward(ball: Ball, side: Side) = if (side == Side.PLAYER) ball.vx < 0 else ball.vx > 0

/** The ball's trailing edge is past [side]'s face, so that paddle can no longer hit it. */
private fun pastFace(ball: Ball, side: Side) =
    if (side == Side.PLAYER) ball.right <= Paddle.face(side) else ball.left >= Paddle.face(side)

/** When a point at [p] moving at [v] enters the open interval (lo, hi); +∞ if it never does. */
private fun entry(p: Double, v: Double, lo: Double, hi: Double) = when {
    v != 0.0 -> min((lo - p) / v, (hi - p) / v)
    p > lo && p < hi -> Double.NEGATIVE_INFINITY
    else -> Double.POSITIVE_INFINITY
}

/** When a point at [p] moving at [v] leaves the open interval (lo, hi); −∞ if it is never inside. */
private fun exit(p: Double, v: Double, lo: Double, hi: Double) = when {
    v != 0.0 -> max((lo - p) / v, (hi - p) / v)
    p > lo && p < hi -> Double.POSITIVE_INFINITY
    else -> Double.NEGATIVE_INFINITY
}

private fun paddleContact(ball: Ball, side: Side, top: Double): Contact? {
    val reach = Reach(side, top)
    val xIn = entry(ball.cx, ball.vx, reach.left, reach.right)
    val yIn = entry(ball.cy, ball.vy, reach.top, reach.bottom)
    val time = max(xIn, yIn)
    if (time < 0 || time >= min(exit(ball.cx, ball.vx, reach.left, reach.right), exit(ball.cy, ball.vy, reach.top, reach.bottom))) {
        return null
    }
    val atContact = ball.copy(cx = ball.cx + ball.vx * time, cy = ball.cy + ball.vy * time)
    if (xIn >= yIn) {
        return if (movingToward(ball, side)) {
            Contact(time, atContact.copy(cx = reach.front).bouncedOff(side, offset(atContact, reach.centreY)), SoundEvent.PADDLE)
        } else {
            Contact(time, atContact.copy(cx = reach.back, vx = -ball.vx), null)
        }
    }
    val onCap = atContact.copy(cy = if (ball.vy > 0) reach.top else reach.bottom)
    return if (movingToward(ball, side) && !pastFace(atContact, side)) {
        Contact(time, onCap.bouncedOff(side, if (ball.vy > 0) -1.0 else 1.0), SoundEvent.PADDLE)
    } else {
        Contact(time, onCap.copy(vy = -ball.vy), null)
    }
}

/**
 * A paddle can move onto the ball. If the ball is wholly past the face it is set behind the paddle and
 * goes on to the goal line; if it is moving away it is set in front of the face. Either way nothing
 * else changes. Otherwise the nearer end cap strikes it, from in front of the face, so the ball's y
 * never jumps.
 */
private fun pushedOut(ball: Ball, side: Side, top: Double): Contact? {
    val reach = Reach(side, top)
    if (!reach.overlaps(ball)) return null
    return when {
        pastFace(ball, side) -> Contact(0.0, ball.copy(cx = reach.back), null)
        !movingToward(ball, side) -> Contact(0.0, ball.copy(cx = reach.front), null)
        else -> {
            val o = if (ball.cy < reach.centreY) -1.0 else 1.0
            Contact(0.0, ball.copy(cx = reach.front).bouncedOff(side, o), SoundEvent.PADDLE)
        }
    }
}

/** Where the ball's centre will be, in y, when it reaches x [atX], folding its path at the walls. */
internal fun interceptY(ball: Ball, atX: Double): Double {
    if (ball.vx == 0.0) return ball.cy
    val time = (atX - ball.cx) / ball.vx
    if (time <= 0) return ball.cy
    val span = Field.HEIGHT - Ball.SIZE
    val unfolded = ball.cy - Ball.HALF + ball.vy * time
    val folded = unfolded.mod(2 * span)
    return Ball.HALF + if (folded > span) 2 * span - folded else folded
}
