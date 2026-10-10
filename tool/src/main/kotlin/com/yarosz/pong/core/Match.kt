package com.yarosz.pong.core

import kotlin.math.abs
import kotlin.random.Random

/**
 * One match against the computer, first to [WINNING_SCORE]. The simulation runs in fixed steps of
 * 1/240 s: the same seed and the same calls in the same order give the same match, bit for bit.
 * The renderer reads the public state each frame; it changes only through the input calls and [advance].
 */
class Match(val seed: Long, val difficulty: Difficulty) {

    private val serveRng: Random
    private val computer: Computer

    init {
        val streams = Random(seed)
        serveRng = Random(streams.nextLong())
        computer = Computer(difficulty, Random(streams.nextLong()))
    }

    /** Setting the Phase restarts [phaseSteps]. */
    var phase: Phase = Phase.PlayerServe
        private set(value) {
            field = value
            phaseSteps = 0
        }

    /** Whole Steps run in the current Phase; restarted by a Phase change, a resume, and paddle input on the player's serve. */
    internal var phaseSteps = 0
        private set
    var paused = false
        private set
    var playerScore = 0
        private set
    var computerScore = 0
        private set

    /** The ball's last state. It is still while a serve is pending and frozen during a Hold. */
    var ball = Ball.AT_CENTRE
        private set

    var playerTarget = Paddle.CENTRE_TOP
        private set
    var playerTop = Paddle.CENTRE_TOP
        private set
    val computerTop get() = computer.top

    private val pendingMoves = ArrayDeque<Double>()
    private var frameUnits = 0L

    val visibleBall: Ball? get() = ball.takeUnless { phase is Phase.PointPause || phase is Phase.MatchOver }
    private val inPlay get() = !paused && phase !is Phase.MatchOver
    val keepScreenOn get() = inPlay
    val servePromptShown get() = !paused && phase is Phase.PlayerServe
    val server get() = if ((playerScore + computerScore) % 2 == 0) Side.PLAYER else Side.COMPUTER

    /** Queues [detents] wheel detents for the next step: positive moves the paddle down. */
    fun turn(detents: Int) {
        if (inPlay) pendingMoves.addLast(detents * DETENT)
    }

    /** Queues a touch drag of [dy] field pixels for the next step. */
    fun drag(dy: Double) {
        if (inPlay) pendingMoves.addLast(dy)
    }

    fun serve() {
        if (inPlay && phase is Phase.PlayerServe) launch(toward = Side.COMPUTER)
    }

    /** The wheel click's in-match meaning (spec item 21). Paused and Match over belong to the menus. */
    fun click() {
        if (!inPlay) return
        when (phase) {
            is Phase.PlayerServe -> serve()
            is Phase.Rally, is Phase.Hold, is Phase.ComputerServe -> pause()
            is Phase.PointPause, is Phase.MatchOver -> Unit
        }
    }

    fun pause() {
        if (!inPlay) return
        paused = true
        frameUnits = 0
        pendingMoves.clear()
    }

    fun resume() {
        if (!paused) return
        paused = false
        when (phase) {
            Phase.Rally, Phase.Hold -> phase = Phase.Hold
            Phase.PlayerServe, Phase.ComputerServe -> phaseSteps = 0
            Phase.PointPause -> afterPoint()
            is Phase.MatchOver -> Unit
        }
    }

    /**
     * Runs the whole steps that fit in [frameNanos], clamped to [MAX_FRAME_NANOS], carrying the
     * remainder to the next frame. Returns the sounds those steps made, in order.
     */
    fun advance(frameNanos: Long): List<SoundEvent> {
        if (!inPlay) return emptyList()
        frameUnits += frameNanos.coerceIn(0, MAX_FRAME_NANOS) * STEPS_PER_SECOND
        var steps = frameUnits / NANOS_PER_SECOND
        frameUnits %= NANOS_PER_SECOND
        val sounds = mutableListOf<SoundEvent>()
        while (steps-- > 0 && inPlay) sounds += step()
        return sounds
    }

    internal fun step(): List<SoundEvent> {
        if (!inPlay) return emptyList()
        val sounds = mutableListOf<SoundEvent>()
        applyPendingMoves()
        easePlayer()
        computer.step(ball.takeIf { phase is Phase.Rally || phase is Phase.Hold })
        when (phase) {
            Phase.PlayerServe -> if (++phaseSteps >= IDLE_PAUSE_STEPS) pause()
            Phase.ComputerServe -> if (++phaseSteps >= COMPUTER_SERVE_STEPS) launch(toward = Side.PLAYER)
            Phase.Hold -> if (++phaseSteps >= PAUSE_STEPS) phase = Phase.Rally
            Phase.PointPause -> if (++phaseSteps >= PAUSE_STEPS) afterPoint()
            Phase.Rally -> sounds += rally()
            is Phase.MatchOver -> Unit
        }
        return sounds
    }

    private fun applyPendingMoves() {
        if (pendingMoves.isEmpty()) return
        while (pendingMoves.isNotEmpty()) playerTarget = Paddle.clampTop(playerTarget + pendingMoves.removeFirst())
        if (phase == Phase.PlayerServe) phaseSteps = 0
    }

    private fun easePlayer() {
        playerTop += (playerTarget - playerTop) * EASE_ALPHA
        if (abs(playerTarget - playerTop) <= SNAP_DISTANCE) playerTop = playerTarget
    }

    private fun rally(): List<SoundEvent> {
        val result = rallyStep(ball, playerTop, computer.top, STEP_SECONDS)
        ball = result.ball
        val scorer = result.scorer ?: return result.sounds
        if (scorer == Side.PLAYER) playerScore++ else computerScore++
        phase = Phase.PointPause
        return result.sounds + SoundEvent.POINT
    }

    private fun afterPoint() {
        ball = Ball.AT_CENTRE
        phase = when {
            playerScore == WINNING_SCORE -> Phase.MatchOver(Side.PLAYER)
            computerScore == WINNING_SCORE -> Phase.MatchOver(Side.COMPUTER)
            server == Side.PLAYER -> Phase.PlayerServe
            else -> Phase.ComputerServe
        }
    }

    private fun launch(toward: Side) {
        ball = Ball.served(toward, serveRng)
        phase = Phase.Rally
    }

    companion object {
        const val WINNING_SCORE = 11
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MAX_FRAME_NANOS = 50_000_000L

        const val COMPUTER_SERVE_STEPS = 192
        /** The 0.6 s of both the Point pause and the Hold (C2). */
        const val PAUSE_STEPS = 144
        const val IDLE_PAUSE_STEPS = 60 * STEPS_PER_SECOND

        const val DETENT = 40.0
        const val SNAP_DISTANCE = 0.5
        private const val EASE_TAU_SECONDS = 0.030
        val EASE_ALPHA = 1 - StrictMath.exp(-STEP_SECONDS / EASE_TAU_SECONDS)
    }
}
