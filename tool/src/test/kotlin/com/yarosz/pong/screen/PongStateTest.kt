package com.yarosz.pong.screen

import com.yarosz.pong.core.Ball
import com.yarosz.pong.core.Difficulty
import com.yarosz.pong.core.Match
import com.yarosz.pong.core.Paddle
import com.yarosz.pong.core.Phase
import com.yarosz.pong.core.Sensitivity
import com.yarosz.pong.core.Side
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PongStateTest {

    @Test
    fun `title preselects the persisted difficulty`() {
        assertEquals(TitleRow.NORMAL, newState().titleRow)
        assertEquals(TitleRow.HARD, newState(Settings(Difficulty.HARD)).titleRow)
        assertEquals(Mode.TITLE, newState().mode)
        assertFalse(newState().shell.awake)
    }

    @Test
    fun `title selection clamps at both ends`() {
        val state = newState()
        repeat(5) { state.wheel(Command.UP) }
        assertEquals(TitleRow.EASY, state.titleRow)
        repeat(9) { state.wheel(Command.DOWN) }
        assertEquals(TitleRow.WHEEL, state.titleRow)
    }

    @Test
    fun `clicking the wheel row toggles the Sensitivity and starts nothing`() {
        val state = newState()
        repeat(4) { state.wheel(Command.DOWN) }
        assertEquals(TitleRow.WHEEL, state.titleRow)
        state.wheel(Command.CLICK)
        assertEquals(Settings(Difficulty.NORMAL, soundOn = true, Sensitivity.FAST), state.settings)
        assertEquals(Mode.TITLE, state.mode)
        state.tapTitle(TitleRow.WHEEL)
        assertEquals(Sensitivity.NORMAL, state.settings.sensitivity)
    }

    @Test
    fun `the match line names the sound choice`() {
        val state = newState(Settings(soundOn = false))
        state.tapTitle(TitleRow.HARD)
        assertTrue("match seed=42 difficulty=hard sensitivity=normal sound=off" in state.drainLog())
    }

    @Test
    fun `a match plays at the Sensitivity chosen on Title`() {
        val state = newState(Settings(sensitivity = Sensitivity.FAST))
        state.tapTitle(TitleRow.NORMAL)
        assertEquals(Sensitivity.FAST, state.match?.sensitivity)
        assertTrue("match seed=42 difficulty=normal sensitivity=fast sound=on" in state.drainLog())
        val start = state.match!!.playerTarget
        state.key(KeyPress.down(WHEEL_DOWN, 0, atMillis = 1_000))
        state.advance(STEP_NANOS)
        assertEquals(start + 60, state.match!!.playerTarget)
    }

    @Test
    fun `the title labels and hint name each row's choice`() {
        val normal = Settings()
        val fast = Settings(soundOn = false, sensitivity = Sensitivity.FAST)
        assertEquals("Wheel normal", TitleRow.WHEEL.label(normal))
        assertEquals("Wheel fast", TitleRow.WHEEL.label(fast))
        assertEquals("Sound on", TitleRow.SOUND.label(normal))
        assertEquals("Sound off", TitleRow.SOUND.label(fast))
        assertEquals("Easy", TitleRow.EASY.label(normal))
        for (row in listOf(TitleRow.EASY, TitleRow.NORMAL, TitleRow.HARD)) {
            assertEquals("Turn the wheel to choose. Press it to start.", row.hint(normal))
        }
        assertEquals("Press the wheel to turn sound off.", TitleRow.SOUND.hint(normal))
        assertEquals("Press the wheel to turn sound on.", TitleRow.SOUND.hint(fast))
        assertEquals("Press the wheel to make it fast.", TitleRow.WHEEL.hint(normal))
        assertEquals("Press the wheel to make it normal.", TitleRow.WHEEL.hint(fast))
    }

    @Test
    fun `a spin in play is logged once the wheel has been quiet for 150 ms`() {
        val state = stateIn(Mode.PLAY)
        state.drainLog()
        for (at in listOf(1_000L, 1_040L, 1_080L)) state.key(KeyPress.down(WHEEL_DOWN, 0, at))
        state.wheelQuiet(1_229)
        assertEquals(emptyList(), state.drainLog())
        state.wheelQuiet(1_230)
        assertEquals(listOf("spin detents=3 ms=80 peak_rate=25"), state.drainLog())
    }

    @Test
    fun `the logged peak rate is the rate the Match applied, also in the Match after Menu`() {
        val state = stateIn(Mode.PLAY)
        for (at in listOf(1_000L, 1_030L, 1_045L)) state.key(KeyPress.down(WHEEL_DOWN, 0, at))
        state.pause()
        state.wheel(Command.DOWN)
        state.wheel(Command.CLICK)
        state.tapTitle(TitleRow.NORMAL)
        assertTrue("spin detents=3 ms=45 peak_rate=44" in state.drainLog())
        val start = state.match!!.playerTarget
        for (at in listOf(1_060L, 1_100L)) state.key(KeyPress.down(WHEEL_DOWN, 0, at))
        state.advance(STEP_NANOS)
        assertEquals(start + 40 + 60, state.match!!.playerTarget, "the new Match reads 1,060 as lone and 1,100 as 25 a second")
        state.pause()
        assertTrue("spin detents=2 ms=40 peak_rate=25" in state.drainLog())
    }

    @Test
    fun `the benchmark Rally holds the speed cap, then pauses once and stays done`() {
        val state = newState()
        state.startBenchmarkRally()
        var finished = 0
        repeat((PongState.BENCHMARK_NANOS / FRAME_NANOS).toInt() + 10) {
            if (state.benchmarkFrame(FRAME_NANOS)) finished++
            state.advance(FRAME_NANOS)
            if (state.mode == Mode.PLAY) assertEquals(Ball.MAX_SPEED, abs(state.match!!.ball.vx))
        }
        assertEquals(1, finished)
        assertEquals(Mode.PAUSED, state.mode)
        assertEquals(0, state.match!!.playerScore + state.match!!.computerScore)
        state.wheel(Command.CLICK)
        repeat(100) {
            assertFalse(state.benchmarkFrame(FRAME_NANOS))
            state.advance(FRAME_NANOS)
        }
        assertEquals(Mode.PLAY, state.mode)
    }

    @Test
    fun `a spin cut short by a pause is logged before the pause`() {
        val state = stateIn(Mode.PLAY)
        state.drainLog()
        state.key(KeyPress.down(WHEEL_UP, 0, 500))
        state.wheel(Command.CLICK)
        state.wheel(Command.CLICK)
        assertEquals(listOf("state=play rally", "spin detents=1 ms=0 peak_rate=0", "state=paused"), state.drainLog())
    }

    @Test
    fun `turning the wheel on Title or a panel logs no spin`() {
        val state = newState()
        state.key(KeyPress.down(WHEEL_DOWN, 0, 0))
        state.wheelQuiet(1_000)
        val paused = stateIn(Mode.PAUSED)
        paused.drainLog()
        paused.key(KeyPress.down(WHEEL_DOWN, 0, 0))
        paused.wheelQuiet(1_000)
        assertEquals(listOf("state=title"), state.drainLog())
        assertEquals(emptyList(), paused.drainLog())
    }

    @Test
    fun `clicking the sound row toggles sound and starts nothing`() {
        val state = newState()
        repeat(2) { state.wheel(Command.DOWN) }
        state.wheel(Command.CLICK)
        assertEquals(Settings(Difficulty.NORMAL, soundOn = false), state.settings)
        assertEquals(Mode.TITLE, state.mode)
        state.tapTitle(TitleRow.SOUND)
        assertTrue(state.settings.soundOn)
    }

    @Test
    fun `clicking a difficulty starts a match there and remembers it`() {
        val state = newState()
        state.wheel(Command.UP)
        state.wheel(Command.CLICK)
        assertEquals(Mode.PLAY, state.mode)
        assertEquals(Difficulty.EASY, state.match?.difficulty)
        assertEquals(Difficulty.EASY, state.settings.difficulty)
        assertEquals(Phase.PlayerServe, state.match?.phase)
        assertTrue(state.shell.awake)
        assertTrue(state.shell.servePrompt)
    }

    @Test
    fun `tapping a difficulty row starts a match there`() {
        val state = newState()
        state.tapTitle(TitleRow.HARD)
        assertEquals(Difficulty.HARD, state.match?.difficulty)
        assertEquals(TitleRow.HARD, state.titleRow)
    }

    @Test
    fun `in play the wheel moves the paddle, up is up`() {
        val state = stateIn(Mode.PLAY)
        val start = state.match!!.playerTarget
        state.command(Command.UP, atMillis = 0)
        state.advance(STEP_NANOS)
        assertEquals(start - 40, state.match!!.playerTarget)
        state.command(Command.DOWN, atMillis = 1_000)
        state.command(Command.DOWN, atMillis = 2_000)
        state.advance(STEP_NANOS)
        assertEquals(start + 40, state.match!!.playerTarget)
    }

    @Test
    fun `click serves, then pauses the rally with Resume preselected`() {
        val state = stateIn(Mode.PLAY)
        state.wheel(Command.CLICK)
        assertEquals(Phase.Rally, state.match?.phase)
        state.advance(FRAME_NANOS)
        state.wheel(Command.CLICK)
        assertEquals(Mode.PAUSED, state.mode)
        assertEquals(MenuRow.FIRST, state.menuRow)
        assertFalse(state.shell.awake)
    }

    @Test
    fun `resume from a rally goes through the hold`() {
        val state = stateIn(Mode.PLAY)
        state.wheel(Command.CLICK)
        state.advance(FRAME_NANOS)
        state.wheel(Command.CLICK)
        state.wheel(Command.CLICK)
        assertEquals(Mode.PLAY, state.mode)
        assertEquals(Phase.Hold, state.match?.phase)
    }

    @Test
    fun `menu selection clamps and Resume is preselected on every pause`() {
        val state = stateIn(Mode.PAUSED)
        repeat(3) { state.wheel(Command.DOWN) }
        assertEquals(MenuRow.MENU, state.menuRow)
        repeat(3) { state.wheel(Command.UP) }
        assertEquals(MenuRow.FIRST, state.menuRow)
        state.wheel(Command.DOWN)
        state.tapMenu(MenuRow.FIRST)
        assertEquals(Mode.PLAY, state.mode)
        state.pause()
        assertEquals(MenuRow.FIRST, state.menuRow)
    }

    @Test
    fun `Menu ends the match and returns to Title on its difficulty`() {
        val state = newState()
        state.tapTitle(TitleRow.HARD)
        state.pause()
        state.wheel(Command.DOWN)
        state.wheel(Command.CLICK)
        assertEquals(Mode.TITLE, state.mode)
        assertNull(state.match)
        assertEquals(TitleRow.HARD, state.titleRow)
    }

    @Test
    fun `a match plays out to Match over with Play again preselected`() {
        val state = stateIn(Mode.OVER)
        val match = state.match!!
        assertEquals(Side.COMPUTER, state.shell.winner)
        assertEquals(Match.WINNING_SCORE, match.computerScore)
        assertEquals(MenuRow.FIRST, state.menuRow)
        assertFalse(state.shell.awake)
        assertNull(match.visibleBall)
    }

    @Test
    fun `Play again starts a fresh match at the same difficulty`() {
        var seed = 0L
        val state = PongState(Settings(Difficulty.EASY)) { ++seed }
        state.wheel(Command.CLICK)
        state.playUntil { mode == Mode.OVER }
        val over = state.match
        state.wheel(Command.CLICK)
        assertNotSame(over, state.match)
        assertEquals(2L, state.match?.seed)
        assertEquals(Difficulty.EASY, state.match?.difficulty)
        assertEquals(Phase.PlayerServe, state.match?.phase)
        assertEquals(0, state.match?.playerScore)
    }

    @Test
    fun `Menu from Match over returns to Title`() {
        val state = stateIn(Mode.OVER)
        state.tapMenu(MenuRow.MENU)
        assertEquals(Mode.TITLE, state.mode)
        assertEquals(TitleRow.NORMAL, state.titleRow)
    }

    @Test
    fun `menus ignore taps meant for another mode`() {
        val title = newState()
        title.tapMenu(MenuRow.MENU)
        title.tapField(540.0, 600.0)
        assertEquals(Mode.TITLE, title.mode)
        val paused = stateIn(Mode.PAUSED)
        paused.tapTitle(TitleRow.EASY)
        assertEquals(Difficulty.NORMAL, paused.match?.difficulty)
    }

    @Test
    fun `a tap in the pause target pauses and elsewhere serves`() {
        val state = stateIn(Mode.PLAY)
        state.tapField(540.0 + 73, 88.0)
        assertEquals(Phase.Rally, state.match?.phase)
        state.tapField(540.0, 400.0)
        assertEquals(Mode.PLAY, state.mode)
        state.tapField(540.0 - 72, 88.0 - 72)
        assertEquals(Mode.PAUSED, state.mode)
    }

    @Test
    fun `a drag moves the paddle target 1 to 1`() {
        val state = stateIn(Mode.PLAY)
        val start = state.match!!.playerTarget
        state.drag(-123.0)
        state.advance(STEP_NANOS)
        assertEquals(start - 123.0, state.match!!.playerTarget)
    }

    @Test
    fun `leaving the screen while Paused selects Resume again`() {
        val state = stateIn(Mode.PAUSED)
        state.wheel(Command.DOWN)
        state.pause()
        assertEquals(Mode.PAUSED, state.mode)
        assertEquals(MenuRow.FIRST, state.menuRow)
    }

    @Test
    fun `the panel keeps its selection between inputs`() {
        val state = stateIn(Mode.PAUSED)
        state.wheel(Command.DOWN)
        state.advance(FRAME_NANOS)
        state.tapField(540.0, 88.0)
        assertEquals(MenuRow.MENU, state.menuRow)
    }

    @Test
    fun `awake is the match's keep-screen-on`() {
        assertFalse(newState().shell.awake)
        val state = stateIn(Mode.PLAY)
        assertTrue(state.shell.awake)
        state.wheel(Command.CLICK)
        state.advance(FRAME_NANOS)
        assertTrue(state.shell.awake)
        state.pause()
        assertFalse(state.shell.awake)
        assertFalse(stateIn(Mode.OVER).shell.awake)
    }

    @Test
    fun `a frame plays only the last of its sounds`() {
        val state = stateIn(Mode.PLAY)
        val twin = Match(42, Difficulty.NORMAL)
        state.wheel(Command.CLICK)
        twin.click()
        var several = 0
        var frames = 0
        while (state.mode == Mode.PLAY && several < 3 && frames++ < 100_000) {
            val follow = twin.ball.cy - twin.playerTarget - Paddle.HALF_LENGTH
            state.drag(follow)
            twin.drag(follow)
            val sounds = twin.advance(Match.MAX_FRAME_NANOS)
            assertEquals(sounds.lastOrNull(), state.advance(Match.MAX_FRAME_NANOS))
            if (sounds.size > 1) several++
            if (twin.phase is Phase.PlayerServe) {
                state.wheel(Command.CLICK)
                twin.click()
            }
        }
        assertEquals(3, several)
    }

    @Test
    fun `a frame plays no sound with sound off`() {
        val state = newState(Settings(soundOn = false))
        state.wheel(Command.CLICK)
        state.wheel(Command.CLICK)
        repeat(60 * 30) {
            assertNull(state.advance(FRAME_NANOS))
            if (state.match?.phase is Phase.PlayerServe) state.wheel(Command.CLICK)
        }
        assertTrue(state.match!!.playerScore + state.match!!.computerScore > 0)
    }

    @Test
    fun `pausing leaves Title and Match over as they are`() {
        val title = newState()
        title.wheel(Command.DOWN)
        title.pause()
        assertEquals(Mode.TITLE, title.mode)
        assertEquals(TitleRow.HARD, title.titleRow)
        val over = stateIn(Mode.OVER)
        over.wheel(Command.DOWN)
        over.pause()
        assertEquals(Mode.OVER, over.mode)
        assertEquals(MenuRow.MENU, over.menuRow)
    }

    @Test
    fun `idle at the player's serve for 60 s pauses`() {
        val state = stateIn(Mode.PLAY)
        repeat(60 * 60 + 1) { state.advance(FRAME_NANOS) }
        assertEquals(Mode.PAUSED, state.mode)
        assertEquals(listOf("state=paused"), state.drainLog().takeLast(1))
    }

    @Test
    fun `the event log names state changes, points and paddle hits`() {
        val state = newState(seed = 7)
        state.wheel(Command.CLICK)
        state.wheel(Command.CLICK)
        state.playUntil { match!!.playerScore + match!!.computerScore == 2 }
        state.pause()
        val log = state.drainLog()
        assertEquals("state=title", log[0])
        assertEquals("match seed=7 difficulty=normal sensitivity=normal sound=on", log[1])
        assertEquals("state=play serve=player", log[2])
        assertEquals("state=play rally", log[3])
        val points = log.filter { it.startsWith("point=") }
        assertEquals(2, points.size)
        assertTrue(points[0].endsWith("score=0-1") || points[0].endsWith("score=1-0"), points[0])
        assertTrue("state=play serve=computer" in log, log.toString())
        assertTrue(log.all { it.matches(Regex("""state=\w+( \S+)?|match seed=\d+ difficulty=\w+ sensitivity=(normal|fast) sound=(on|off)|point=(player|computer) score=\d+-\d+|hit=paddle side=(player|computer)""")) }, log.toString())
        assertEquals("state=paused", log.last())
        assertTrue(state.drainLog().isEmpty())
    }

    @Test
    fun `a paddle hit is logged with the side whose half the ball is in`() {
        val state = stateIn(Mode.PLAY)
        val hits = mutableListOf<String>()
        while (state.mode != Mode.OVER) {
            if (state.match?.phase is Phase.PlayerServe) state.wheel(Command.CLICK) else state.advance(FRAME_NANOS)
            val side = if (state.match!!.ball.cx < 540) "player" else "computer"
            state.drainLog().filter { it.startsWith("hit=") }.forEach {
                assertEquals("hit=paddle side=$side", it)
                hits += it
            }
        }
        assertTrue("hit=paddle side=computer" in hits, hits.toString())
    }

    @Test
    fun `Match over is logged with the winner`() {
        val state = stateIn(Mode.OVER)
        assertEquals("state=over winner=computer", state.drainLog().last())
    }
}
