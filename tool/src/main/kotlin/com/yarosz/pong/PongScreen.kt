package com.yarosz.pong

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.yarosz.pong.core.Phase
import com.yarosz.pong.screen.FieldLayout
import com.yarosz.pong.screen.FrameLog
import com.yarosz.pong.screen.KeyPress
import com.yarosz.pong.screen.Letterbox
import com.yarosz.pong.screen.Mode
import com.yarosz.pong.screen.PongOwner
import com.yarosz.pong.screen.PongState
import com.yarosz.pong.screen.SettingsFile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The view onto [PongOwner]'s state. Every input runs through [update], which saves the settings when
 * they change, logs the PongEvent lines, stops any tone when play stops, and publishes the [shell] and
 * the [frame] the field's draw phase reads.
 */
class PongViewModel(private val state: PongState, private val settingsFile: SettingsFile) : LightViewModel<Unit>() {

    var shell by mutableStateOf(state.shell)
        private set
    val frame = mutableIntStateOf(0)
    val match get() = state.match

    /** Set while the screen is composed; released with it. */
    var tones: Tones? = null

    private val frameLog = FrameLog()

    /** How long the field's last draw took, measured around [drawField] on the main thread. */
    var drawNanos = 0L

    init {
        update { if (BuildConfig.BENCHMARK_RALLY && match == null) startBenchmarkRally() }
    }

    fun update(block: PongState.() -> Unit) {
        val before = state.settings
        state.block()
        if (state.settings != before) runCatching { settingsFile.save(state.settings) }.onFailure { Log.w(TAG, "settings not saved", it) }
        state.drainLog().forEach { Log.d(EVENT_TAG, it) }
        shell = state.shell
        if (shell.mode != Mode.PLAY) tones?.stop()
        frame.intValue++
    }

    fun frame(intervalNanos: Long) {
        var benchmarkDone = false
        update {
            if (BuildConfig.BENCHMARK_RALLY) benchmarkDone = benchmarkFrame(intervalNanos)
            advance(intervalNanos)?.let { tones?.play(it) }
            wheelQuiet(SystemClock.uptimeMillis())
        }
        if (benchmarkDone) Log.d(PERF_TAG, "done")
        val current = match
        if (shell.mode == Mode.PLAY && current?.phase == Phase.Rally) {
            frameLog.record(intervalNanos, current.frameSteps, drawNanos, abs(current.ball.vx))?.let { Log.d(PERF_TAG, it) }
        } else {
            frameLog.reset()
        }
    }

    private fun key(press: KeyPress): Boolean {
        var consumed = false
        update { consumed = key(press) }
        return consumed
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) = key(KeyPress.down(keyCode, event.repeatCount, event.eventTime))

    override fun onKeyUp(keyCode: Int, event: KeyEvent) = key(KeyPress.up(keyCode, event.repeatCount, event.eventTime))

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent) = key(KeyPress.multiple(keyCode, repeatCount, event.eventTime))

    override fun onAppPause() = update { pause() }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) = update { pause() }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) = update {}

    companion object {
        const val TAG = "Pong"
        const val EVENT_TAG = "PongEvent"
        const val PERF_TAG = "PongPerf"
    }
}

@InitialScreen
class PongScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, PongViewModel>(sealedActivity) {

    override val viewModelClass: Class<PongViewModel>
        get() = PongViewModel::class.java

    override fun createViewModel(): PongViewModel {
        val file = SettingsFile(lightContext.filesDir)
        return PongViewModel(PongOwner.attach(file::load), file)
    }

    @Composable
    override fun Content() {
        val vm = viewModel
        val shell = vm.shell
        val tones = remember { Tones() }
        DisposableEffect(tones) {
            vm.tones = tones
            onDispose {
                vm.tones = null
                tones.release()
            }
        }
        LaunchedEffect(shell.mode == Mode.PLAY) {
            if (shell.mode != Mode.PLAY) return@LaunchedEffect
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) vm.frame(now - last)
                    last = now
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { View(it) }, modifier = Modifier.size(0.dp), update = { it.keepScreenOn = shell.awake })
            if (shell.mode == Mode.TITLE) {
                Title(shell) { row -> vm.update { tapTitle(row) } }
            } else {
                Field(vm, play = shell.mode == Mode.PLAY)
                if (shell.servePrompt) ServePrompt(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                if (shell.mode == Mode.PAUSED || shell.mode == Mode.OVER) Panel(shell) { row -> vm.update { tapMenu(row) } }
            }
        }
    }

    @Composable
    private fun Field(vm: PongViewModel, play: Boolean) {
        val touch = if (play) Modifier.pointerInput(Unit) { fieldGestures(vm) } else Modifier
        Canvas(Modifier.fillMaxSize().then(touch)) {
            vm.frame.intValue
            val start = System.nanoTime()
            vm.match?.let { drawField(it, pauseBars = play) }
            vm.drawNanos = System.nanoTime() - start
        }
        if (play) PauseControl { vm.update { tapField(FieldLayout.CENTRE_X.toDouble(), FieldLayout.PAUSE_TARGET_Y.toDouble()) } }
    }
}

/**
 * DESIGN.md items 6 and 30: the pause bars' accessibility node, a transparent button over the letterboxed
 * 144x144 target. It takes no pointer input, so a touch there still reaches the field's gestures.
 */
@Composable
private fun PauseControl(onPause: () -> Unit) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val target = Letterbox(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()).pauseTarget()
    val side = with(LocalDensity.current) { (target[2] - target[0]).toDp() }
    Box(
        Modifier
            .offset { IntOffset(target[0].roundToInt(), target[1].roundToInt()) }
            .size(side)
            .semantics {
                contentDescription = "Pause"
                role = Role.Button
                onClick {
                    onPause()
                    true
                }
            },
    )
}

/**
 * DESIGN.md item 24: a drag moves the Paddle target 1:1; a touch that stays under slop and ends in a
 * pointer up is a tap. A gesture that loses its pointer or is cancelled does nothing more.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.fieldGestures(vm: PongViewModel) = awaitEachGesture {
    val down = awaitFirstDown()
    val box = Letterbox(size.width.toFloat(), size.height.toFloat())
    var travel = Offset.Zero
    var dragging = false
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
        // Compose delivers a cancel as an up that is already consumed.
        if (!change.pressed && change.isConsumed) return@awaitEachGesture
        val delta = change.positionChange()
        travel += delta
        if (!dragging && travel.getDistance() > viewConfiguration.touchSlop) {
            dragging = true
            vm.update { drag(travel.y / box.scale.toDouble()) }
        } else if (dragging && delta.y != 0f) {
            vm.update { drag(delta.y / box.scale.toDouble()) }
        }
        val up = change.changedToUp()
        change.consume()
        if (up) break
    }
    if (!dragging) vm.update { tapField(box.fieldX(down.position.x), box.fieldY(down.position.y)) }
}
