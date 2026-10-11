package com.yarosz.pong.screen

import com.yarosz.pong.core.Ball
import com.yarosz.pong.core.Difficulty
import com.yarosz.pong.core.Match
import com.yarosz.pong.core.Paddle
import com.yarosz.pong.core.Phase
import com.yarosz.pong.core.Sensitivity
import com.yarosz.pong.core.Side
import com.yarosz.pong.core.SoundEvent
import kotlin.math.abs

/** What the screen shows (DESIGN.md item 31): Play covers every in-match Phase that is not Paused. */
enum class Mode { TITLE, PLAY, PAUSED, OVER }

/** The Title's rows, top to bottom (DESIGN.md item 25): the Difficulties, then the sound and wheel settings. */
enum class TitleRow(val difficulty: Difficulty?) {
    EASY(Difficulty.EASY),
    NORMAL(Difficulty.NORMAL),
    HARD(Difficulty.HARD),
    SOUND(null),
    WHEEL(null),
    ;

    fun label(settings: Settings): String = when (this) {
        SOUND -> if (settings.soundOn) "Sound on" else "Sound off"
        WHEEL -> when (settings.sensitivity) {
            Sensitivity.NORMAL -> "Wheel normal"
            Sensitivity.FAST -> "Wheel fast"
        }
        EASY, NORMAL, HARD -> checkNotNull(difficulty).label()
    }

    /** The hint at the bottom of Title while this row is selected. */
    fun hint(settings: Settings): String = when (this) {
        SOUND -> if (settings.soundOn) "Press the wheel to turn sound off." else "Press the wheel to turn sound on."
        WHEEL -> when (settings.sensitivity) {
            Sensitivity.NORMAL -> "Press the wheel to make it fast."
            Sensitivity.FAST -> "Press the wheel to make it normal."
        }
        EASY, NORMAL, HARD -> "Turn the wheel to choose. Press it to start."
    }

    companion object {
        fun of(difficulty: Difficulty) = entries.first { it.difficulty == difficulty }
    }
}

fun Difficulty.label() = when (this) {
    Difficulty.EASY -> "Easy"
    Difficulty.NORMAL -> "Normal"
    Difficulty.HARD -> "Hard"
}

/** The two rows of the Paused and Match over panels: [FIRST] is Resume or Play again. */
enum class MenuRow { FIRST, MENU }

/** The screen's state at a glance, for recomposition; the field itself is drawn from [PongState.match]. */
data class Shell(
    val mode: Mode,
    val titleRow: TitleRow,
    val menuRow: MenuRow,
    val settings: Settings,
    val difficulty: Difficulty?,
    val winner: Side?,
    val servePrompt: Boolean,
    val awake: Boolean,
)

/**
 * The Tool's whole screen state: the Title selection, the Match in progress and the panel selection.
 * Every input goes through here, so this is where the panel's preselection rule runs
 * ([preselectMenuRow]) and where the PongEvent log lines come from, the wheel's bursts among them
 * ([BurstLog]).
 */
class PongState(settings: Settings, private val newSeed: () -> Long) {

    var settings = settings
        private set
    var titleRow = TitleRow.of(settings.difficulty)
        private set
    var match: Match? = null
        private set
    var menuRow = MenuRow.FIRST
        private set

    private val log = mutableListOf("state=title")
    private val burst = BurstLog()

    /** Frame time of the benchmark Rally so far; null outside it. */
    private var benchmarkNanos: Long? = null

    val mode: Mode
        get() {
            val current = match ?: return Mode.TITLE
            return when {
                current.phase is Phase.MatchOver -> Mode.OVER
                current.paused -> Mode.PAUSED
                else -> Mode.PLAY
            }
        }

    val shell: Shell
        get() = Shell(
            mode = mode,
            titleRow = titleRow,
            menuRow = menuRow,
            settings = settings,
            difficulty = match?.difficulty,
            winner = (match?.phase as? Phase.MatchOver)?.winner,
            servePrompt = match?.servePromptShown == true,
            awake = match?.keepScreenOn == true,
        )

    /** The PongEvent lines since the last call. */
    fun drainLog(): List<String> = log.toList().also { log.clear() }

    /** One key from any of the three hooks: returns whether it is consumed. */
    fun key(press: KeyPress): Boolean {
        val decision = decideKey(press)
        decision.command?.let { command(it, press.atMillis) }
        return decision.consumed
    }

    /** One wheel [command]; [atMillis] is its key's event time, which only a turn in play reads. */
    fun command(command: Command, atMillis: Long) = tracked {
        val current = match
        when (mode) {
            Mode.TITLE -> when (command) {
                Command.UP -> titleRow = TitleRow.entries[(titleRow.ordinal - 1).coerceAtLeast(0)]
                Command.DOWN -> titleRow = TitleRow.entries[(titleRow.ordinal + 1).coerceAtMost(TitleRow.entries.lastIndex)]
                Command.CLICK -> activate(titleRow)
            }
            Mode.PLAY -> when (command) {
                Command.UP, Command.DOWN -> {
                    val rate = current?.turn(down = command == Command.DOWN, atMillis = atMillis)
                    if (rate != null) burst.detent(atMillis, rate)?.let { log += it }
                }
                Command.CLICK -> current?.click()
            }
            Mode.PAUSED, Mode.OVER -> when (command) {
                Command.UP -> menuRow = MenuRow.FIRST
                Command.DOWN -> menuRow = MenuRow.MENU
                Command.CLICK -> activate(menuRow)
            }
        }
    }

    fun tapTitle(row: TitleRow) = tracked {
        if (mode != Mode.TITLE) return@tracked
        titleRow = row
        activate(row)
    }

    fun tapMenu(row: MenuRow) = tracked {
        if (mode != Mode.PAUSED && mode != Mode.OVER) return@tracked
        menuRow = row
        activate(row)
    }

    /** A tap on the field at ([x], [y]) in field pixels (DESIGN.md item 24). */
    fun tapField(x: Double, y: Double) = tracked {
        val current = match ?: return@tracked
        if (mode != Mode.PLAY) return@tracked
        if (FieldLayout.inPauseTarget(x, y)) current.pause() else current.serve()
    }

    /** A touch drag of [dy] field pixels. */
    fun drag(dy: Double) = tracked {
        if (mode == Mode.PLAY) match?.drag(dy)
    }

    /** Called each frame in play: logs the burst once the wheel is quiet for 150 ms at [nowMillis] (uptime ms). */
    fun wheelQuiet(nowMillis: Long) {
        if (mode == Mode.PLAY) burst.quiet(nowMillis)?.let { log += it }
    }

    /** Runs the Match for one frame; returns the tone to play: the frame's last sound (decision log D19), none with sound off. */
    fun advance(frameNanos: Long): SoundEvent? {
        var sounds = emptyList<SoundEvent>()
        trackedFrame { match?.advance(frameNanos).orEmpty().also { sounds = it } }
        return sounds.lastOrNull()?.takeIf { settings.soundOn }
    }

    /**
     * onAppPause, onScreenHide, or a new view model re-attaching: any in-match Phase shows Paused. The
     * screen is being left or shown again, so Paused shows Resume selected ([preselectMenuRow]).
     */
    fun pause() = tracked(reshown = true) { match?.pause() }

    /** The benchmark build only (decision log D30): opens straight into the endless Rally at the speed cap. */
    fun startBenchmarkRally() = tracked {
        startMatch(settings.difficulty)
        benchmarkNanos = 0
        holdBenchmarkRally()
    }

    /**
     * The benchmark build only, once a frame before [advance]: keeps the Rally endless for
     * [BENCHMARK_NANOS] of frames in play, then pauses. Returns true on the frame that ends the run,
     * once; after it the Match plays as any other.
     */
    fun benchmarkFrame(intervalNanos: Long): Boolean {
        var finished = false
        tracked {
            val elapsed = benchmarkNanos ?: return@tracked
            if (mode != Mode.PLAY) return@tracked
            if (elapsed + intervalNanos < BENCHMARK_NANOS) {
                benchmarkNanos = elapsed + intervalNanos
                holdBenchmarkRally()
            } else {
                benchmarkNanos = null
                match?.pause()
                finished = true
            }
        }
        return finished
    }

    /** Serves a pending Serve, holds the Ball at the speed cap, and puts both Paddles on its line, so neither misses. */
    private fun holdBenchmarkRally() {
        val current = match ?: return
        current.serve()
        if (current.phase != Phase.Rally) return
        val ball = current.ball
        val factor = Ball.MAX_SPEED / abs(ball.vx)
        val capped = ball.copy(vx = ball.vx * factor, vy = ball.vy * factor)
        current.place(capped, Paddle.clampTop(capped.cy - Paddle.HALF_LENGTH))
    }

    private fun activate(row: TitleRow) {
        when (row) {
            TitleRow.SOUND -> settings = settings.copy(soundOn = !settings.soundOn)
            TitleRow.WHEEL -> settings = settings.copy(
                sensitivity = if (settings.sensitivity == Sensitivity.NORMAL) Sensitivity.FAST else Sensitivity.NORMAL,
            )
            TitleRow.EASY, TitleRow.NORMAL, TitleRow.HARD -> {
                val difficulty = checkNotNull(row.difficulty)
                settings = settings.copy(difficulty = difficulty)
                startMatch(difficulty)
            }
        }
    }

    private fun activate(row: MenuRow) {
        val current = match ?: return
        when {
            row == MenuRow.MENU -> {
                titleRow = TitleRow.of(current.difficulty)
                match = null
            }
            mode == Mode.PAUSED -> current.resume()
            else -> startMatch(current.difficulty)
        }
    }

    private fun startMatch(difficulty: Difficulty) {
        val seed = newSeed()
        log += "match seed=$seed difficulty=${difficulty.name.lowercase()} sensitivity=${settings.sensitivity.name.lowercase()} sound=${if (settings.soundOn) "on" else "off"}"
        match = Match(seed, difficulty, settings.sensitivity)
    }

    private class Snapshot(val match: Match?, val mode: Mode, val phase: String?, val playerScore: Int, val computerScore: Int)

    private fun snapshot(): Snapshot {
        val current = match
        return Snapshot(current, mode, current?.phase?.let(::phaseName), current?.playerScore ?: 0, current?.computerScore ?: 0)
    }

    private inline fun tracked(reshown: Boolean = false, block: () -> Unit) = trackedFrame(reshown) {
        block()
        emptyList()
    }

    private inline fun trackedFrame(reshown: Boolean = false, block: () -> List<SoundEvent>) {
        val before = snapshot()
        val sounds = block()
        val after = snapshot()
        val current = after.match
        if (current != null && current === before.match) {
            if (SoundEvent.PADDLE in sounds) {
                val side = if (current.ball.vx > 0) Side.PLAYER else Side.COMPUTER
                log += "hit=paddle side=${side.name.lowercase()}"
            }
            val scorer = when {
                after.playerScore > before.playerScore -> Side.PLAYER
                after.computerScore > before.computerScore -> Side.COMPUTER
                else -> null
            }
            if (scorer != null) log += "point=${scorer.name.lowercase()} score=${after.playerScore}-${after.computerScore}"
        }
        if (before.mode == Mode.PLAY && (after.mode != Mode.PLAY || current !== before.match)) burst.end()?.let { log += it }
        preselectMenuRow(before.mode, after.mode, reshown)
        logMode(before, after)
    }

    /**
     * DESIGN.md items 27-28, decision log D27. Entering Paused or Match over selects the first row
     * (Resume, Play again). Paused shown again after the screen was left selects Resume too, so one press
     * on return never ends the Match. Match over shown again keeps its row: neither row loses anything.
     */
    private fun preselectMenuRow(before: Mode, after: Mode, reshown: Boolean) {
        val entered = after != before
        if ((after == Mode.PAUSED && (entered || reshown)) || (after == Mode.OVER && entered)) menuRow = MenuRow.FIRST
    }

    private fun logMode(before: Snapshot, after: Snapshot) {
        val changed = after.mode != before.mode || after.match !== before.match ||
            (after.mode == Mode.PLAY && after.phase != before.phase && after.phase != POINT)
        if (!changed) return
        log += when (after.mode) {
            Mode.TITLE -> "state=title"
            Mode.PLAY -> "state=play ${after.phase}"
            Mode.PAUSED -> "state=paused"
            Mode.OVER -> "state=over winner=${shell.winner?.name?.lowercase()}"
        }
    }

    companion object {
        const val BENCHMARK_NANOS = 30_000_000_000L
        private const val POINT = "point"

        private fun phaseName(phase: Phase) = when (phase) {
            is Phase.PlayerServe -> "serve=player"
            is Phase.ComputerServe -> "serve=computer"
            is Phase.Rally -> "rally"
            is Phase.Hold -> "hold"
            is Phase.PointPause -> POINT
            is Phase.MatchOver -> "over"
        }
    }
}
