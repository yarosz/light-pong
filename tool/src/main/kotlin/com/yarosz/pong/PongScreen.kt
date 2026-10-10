package com.yarosz.pong

import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.yarosz.pong.core.Phase
import com.yarosz.pong.screen.FrameLog
import com.yarosz.pong.screen.KeyAction
import com.yarosz.pong.screen.Letterbox
import com.yarosz.pong.screen.Mode
import com.yarosz.pong.screen.PongOwner
import com.yarosz.pong.screen.PongState
import com.yarosz.pong.screen.SettingsFile

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

    init {
        update {}
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
        update {
            val sound = advance(intervalNanos)
            if (sound != null && settings.soundOn) tones?.play(sound)
        }
        if (shell.mode == Mode.PLAY && match?.phase == Phase.Rally) {
            frameLog.record(intervalNanos)?.let { Log.d(PERF_TAG, it) }
        } else {
            frameLog.reset()
        }
    }

    private fun key(keyCode: Int, action: KeyAction, repeatCount: Int): Boolean {
        var consumed = false
        update { consumed = key(keyCode, action, repeatCount) }
        return consumed
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) = key(keyCode, KeyAction.DOWN, event.repeatCount)

    override fun onKeyUp(keyCode: Int, event: KeyEvent) = key(keyCode, KeyAction.UP, event.repeatCount)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent) = key(keyCode, KeyAction.MULTIPLE, repeatCount)

    override fun onAppPause() = update { pause() }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) = update { pause() }

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
            vm.match?.let { drawField(it, pauseBars = play) }
        }
    }
}

/** DESIGN.md item 24: a drag moves the Paddle target 1:1; a touch that stays under slop is a tap. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.fieldGestures(vm: PongViewModel) = awaitEachGesture {
    val down = awaitFirstDown()
    val box = Letterbox(size.width.toFloat(), size.height.toFloat())
    var travel = Offset.Zero
    var dragging = false
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        val delta = change.positionChange()
        travel += delta
        if (!dragging && travel.getDistance() > viewConfiguration.touchSlop) {
            dragging = true
            vm.update { drag(travel.y / box.scale.toDouble()) }
        } else if (dragging && delta.y != 0f) {
            vm.update { drag(delta.y / box.scale.toDouble()) }
        }
        change.consume()
        if (!change.pressed) break
    }
    if (!dragging) vm.update { tapField(box.fieldX(down.position.x), box.fieldY(down.position.y)) }
}
