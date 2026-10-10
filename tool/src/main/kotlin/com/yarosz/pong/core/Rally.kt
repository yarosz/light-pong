package com.yarosz.pong.core

import kotlin.math.max
import kotlin.math.min

/** One rally step's result: the moved ball, the sounds it made, and who scored if it crossed a goal line. */
internal class RallyStep(val ball: Ball, val sounds: List<SoundEvent>, val scorer: Side?)

private class Contact(val time: Double, val ball: Ball, val sound: SoundEvent)

// A ball wedged between a wall and an end cap can bounce many times in one step; past this many it
// waits out the rest of the step rather than being moved through either.
private const val MAX_CONTACTS = 16

/**
 * Moves [start] for [seconds] against the walls and both paddles, given each paddle's top after this
 * step's movement. Collisions are swept, so the ball cannot pass through a paddle at any speed.
 */
internal fun rallyStep(start: Ball, playerTop: Double, computerTop: Double, seconds: Double): RallyStep {
    val sounds = mutableListOf<SoundEvent>()
    val tops = mapOf(Side.PLAYER to playerTop, Side.COMPUTER to computerTop)
    var ball = start
    for ((side, top) in tops) {
        ball = pushedOut(ball, side, top)?.also { sounds += SoundEvent.PADDLE } ?: ball
    }
    var remaining = seconds
    var contacts = 0
    while (contacts < MAX_CONTACTS) {
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
        sounds += contact.sound
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

    fun overlaps(ball: Ball) = ball.cx > left && ball.cx < right && ball.cy > top && ball.cy < bottom
}

private fun offset(ball: Ball, centreY: Double) = (ball.cy - centreY) / (Paddle.HALF_LENGTH + Ball.HALF)

/** The interval of times during which a point at [p] moving at [v] lies strictly inside (lo, hi). */
private fun slab(p: Double, v: Double, lo: Double, hi: Double): Pair<Double, Double>? = when {
    v != 0.0 -> {
        val a = (lo - p) / v
        val b = (hi - p) / v
        min(a, b) to max(a, b)
    }
    p > lo && p < hi -> Double.NEGATIVE_INFINITY to Double.POSITIVE_INFINITY
    else -> null
}

private fun paddleContact(ball: Ball, side: Side, top: Double): Contact? {
    val reach = Reach(side, top)
    val (xIn, xOut) = slab(ball.cx, ball.vx, reach.left, reach.right) ?: return null
    val (yIn, yOut) = slab(ball.cy, ball.vy, reach.top, reach.bottom) ?: return null
    val time = max(xIn, yIn)
    if (time < 0 || time >= min(xOut, yOut)) return null
    val atContact = ball.copy(cx = ball.cx + ball.vx * time, cy = ball.cy + ball.vy * time)
    val hit = if (xIn >= yIn) {
        val faceCx = if (side == Side.PLAYER) reach.right else reach.left
        atContact.copy(cx = faceCx).bouncedOff(side, offset(atContact, reach.centreY))
    } else {
        val capCy = if (ball.vy > 0) reach.top else reach.bottom
        atContact.copy(cy = capCy).bouncedOff(side, if (ball.vy > 0) -1.0 else 1.0)
    }
    return Contact(time, hit, SoundEvent.PADDLE)
}

/**
 * A paddle can move onto a ball that has already passed its face. The ball is then struck by the end
 * cap it is nearer, or, when a wall leaves no room beside that cap, set back in front of the face.
 */
private fun pushedOut(ball: Ball, side: Side, top: Double): Ball? {
    val reach = Reach(side, top)
    if (!reach.overlaps(ball)) return null
    val above = ball.cy < reach.centreY
    val capCy = if (above) reach.top else reach.bottom
    if (capCy - Ball.HALF >= 0 && capCy + Ball.HALF <= Field.HEIGHT) {
        return ball.copy(cy = capCy).bouncedOff(side, if (above) -1.0 else 1.0)
    }
    val faceCx = if (side == Side.PLAYER) reach.right else reach.left
    return ball.copy(cx = faceCx).bouncedOff(side, offset(ball, reach.centreY))
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
