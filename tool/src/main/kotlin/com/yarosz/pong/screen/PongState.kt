package com.yarosz.pong.screen

import com.yarosz.pong.core.Difficulty
import com.yarosz.pong.core.Match
import com.yarosz.pong.core.Phase
import com.yarosz.pong.core.Side
import com.yarosz.pong.core.SoundEvent

/** What the screen shows (DESIGN.md item 31): Play covers every in-match Phase that is not Paused. */
enum class Mode { TITLE, PLAY, PAUSED, OVER }

enum class TitleRow(val difficulty: Difficulty?) {
    EASY(Difficulty.EASY),
    NORMAL(Difficulty.NORMAL),
    HARD(Difficulty.HARD),
    SOUND(null),
    ;

    companion object {
        fun of(difficulty: Difficulty) = entries.first { it.difficulty == difficulty }
    }
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
) {
    val awake get() = mode == Mode.PLAY
}

/**
 * The Tool's whole screen state: the Title selection, the Match in progress and the panel selection.
 * Every input goes through here, so this is where a mode change resets the panel selection (Resume and
 * Play again are preselected every time) and where the PongEvent log lines come from.
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
        )

    /** The PongEvent lines since the last call. */
    fun drainLog(): List<String> = log.toList().also { log.clear() }

    /** One key from any of the three hooks: returns whether it is consumed. */
    fun key(keyCode: Int, action: KeyAction, repeatCount: Int): Boolean {
        val decision = decideKey(keyCode, action, repeatCount)
        decision.command?.let(::command)
        return decision.consumed
    }

    fun command(command: Command) = tracked {
        val current = match
        when (mode) {
            Mode.TITLE -> when (command) {
                Command.UP -> titleRow = TitleRow.entries[(titleRow.ordinal - 1).coerceAtLeast(0)]
                Command.DOWN -> titleRow = TitleRow.entries[(titleRow.ordinal + 1).coerceAtMost(TitleRow.entries.lastIndex)]
                Command.CLICK -> activate(titleRow)
            }
            Mode.PLAY -> when (command) {
                Command.UP -> current?.turn(-1)
                Command.DOWN -> current?.turn(1)
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

    /** Runs the Match for one frame; returns the sound to play, the last of the frame's (decision log D19). */
    fun advance(frameNanos: Long): SoundEvent? {
        var sounds = emptyList<SoundEvent>()
        trackedFrame { match?.advance(frameNanos).orEmpty().also { sounds = it } }
        return sounds.lastOrNull()
    }

    /** onAppPause, onScreenHide, or a new view model re-attaching: any in-match Phase shows Paused. */
    fun pause() = tracked { match?.pause() }

    private fun activate(row: TitleRow) {
        val difficulty = row.difficulty
        if (difficulty == null) {
            settings = settings.copy(soundOn = !settings.soundOn)
        } else {
            settings = settings.copy(difficulty = difficulty)
            startMatch(difficulty)
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
        log += "match seed=$seed difficulty=${difficulty.name.lowercase()}"
        match = Match(seed, difficulty)
    }

    private class Snapshot(val match: Match?, val mode: Mode, val phase: String?, val playerScore: Int, val computerScore: Int)

    private fun snapshot(): Snapshot {
        val current = match
        return Snapshot(current, mode, current?.phase?.let(::phaseName), current?.playerScore ?: 0, current?.computerScore ?: 0)
    }

    private inline fun tracked(block: () -> Unit) = trackedFrame {
        block()
        emptyList()
    }

    private inline fun trackedFrame(block: () -> List<SoundEvent>) {
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
        logMode(before, after)
    }

    private fun logMode(before: Snapshot, after: Snapshot) {
        if (after.mode != before.mode && (after.mode == Mode.PAUSED || after.mode == Mode.OVER)) menuRow = MenuRow.FIRST
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

    private companion object {
        const val POINT = "point"

        fun phaseName(phase: Phase) = when (phase) {
            is Phase.PlayerServe -> "serve=player"
            is Phase.ComputerServe -> "serve=computer"
            is Phase.Rally -> "rally"
            is Phase.Hold -> "hold"
            is Phase.PointPause -> POINT
            is Phase.MatchOver -> "over"
        }
    }
}
