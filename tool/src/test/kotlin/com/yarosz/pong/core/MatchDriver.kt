package com.yarosz.pong.core

import kotlin.math.abs
import kotlin.random.Random

/** Everything a renderer can read from a Match, as one comparable value. */
data class Snapshot(
    val phase: Phase,
    val phaseSteps: Int,
    val paused: Boolean,
    val playerScore: Int,
    val computerScore: Int,
    val ball: Ball,
    val playerTarget: Double,
    val playerTop: Double,
    val computerTop: Double,
)

fun Match.snapshot() = Snapshot(phase, phaseSteps, paused, playerScore, computerScore, ball, playerTarget, playerTop, computerTop)

fun Match.steps(count: Int): List<SoundEvent> = (1..count).flatMap { step() }

fun Match.stepUntil(limit: Int = 200_000, done: Match.() -> Boolean) {
    var taken = 0
    while (!done()) {
        check(taken++ < limit) { "no progress after $limit steps: ${snapshot()}" }
        step()
    }
}

const val PLAYER_CONTACT_X = Paddle.PLAYER_LEFT + Paddle.THICKNESS + Ball.HALF

private var slowWheelMillis = 0L

/** Turns [detents] Detents (positive is down) a second apart, so each moves the Paddle target 40 px, as every Detent did in 0.1.0. */
fun Match.turnSlowly(detents: Int) = repeat(abs(detents)) {
    slowWheelMillis += 1_000
    turn(down = detents > 0, atMillis = slowWheelMillis)
}

/** The event time of a Detent sent before Step [steps], in uptime ms. */
fun stepMillis(steps: Long) = steps * 1_000 / STEPS_PER_SECOND

/**
 * A player who turns the wheel at most [detentsPerSecond] times a second, timing each Detent by the
 * Steps it has seen. A perfect bot steers toward the ball's predicted intercept plus a chosen contact
 * offset; a losing bot steers away from it. Each approach, the bot waits [reactionSteps] and misjudges
 * the intercept by up to [misjudgement] px. It turns while the gap is more than half the step its
 * spin rate earns, so a fast spin never overshoots back and forth.
 */
class PlayerBot(
    private val rng: Random,
    private val perfect: Boolean = true,
    detentsPerSecond: Int = 30,
    private val serveDelaySteps: Int = 60,
    private val reactionSteps: Int = 0,
    private val misjudgement: Double = 0.0,
) {
    private val stepsPerDetent = STEPS_PER_SECOND / detentsPerSecond
    private val halfStep = Sensitivity.NORMAL.step(detentsPerSecond.toDouble()) / 2
    private var steps = 0L
    private var cooldown = 0
    private var aim = 0.0
    private var approaching = false
    private var waitedToReact = 0
    private var waitedToServe = 0

    fun act(match: Match) {
        steps++
        if (match.paused || match.phase is Phase.MatchOver) return
        if (match.phase is Phase.PlayerServe && ++waitedToServe >= serveDelaySteps) {
            waitedToServe = 0
            match.serve()
        }
        val goal = goal(match)
        if (cooldown > 0) {
            cooldown--
            return
        }
        val gap = goal - match.playerTarget
        if (abs(gap) > halfStep) {
            match.turn(down = gap > 0, atMillis = stepMillis(steps))
            cooldown = stepsPerDetent - 1
        }
    }

    private fun goal(match: Match): Double {
        val ball = match.visibleBall
        if (ball == null || ball.vx >= 0 || match.phase is Phase.PlayerServe) {
            approaching = false
            return Paddle.CENTRE_TOP
        }
        val intercept = interceptY(ball, PLAYER_CONTACT_X)
        if (!perfect) return if (intercept > Field.HEIGHT / 2) 0.0 else Paddle.MAX_TOP
        if (!approaching) {
            approaching = true
            aim = rng.nextDouble(-0.7, 0.7) * Paddle.HALF_LENGTH
            if (misjudgement > 0) aim += rng.nextDouble(-misjudgement, misjudgement)
            waitedToReact = 0
        }
        if (waitedToReact++ < reactionSteps) return match.playerTarget
        return Paddle.clampTop(intercept - aim - Paddle.HALF_LENGTH)
    }
}

/** Plays [match] to Match over with [bot] as the player, one step at a time. Returns the steps taken. */
fun playOut(match: Match, bot: PlayerBot, limit: Int = 3_000_000): Int {
    var taken = 0
    while (match.phase !is Phase.MatchOver && taken < limit) {
        bot.act(match)
        match.step()
        taken++
    }
    return taken
}
