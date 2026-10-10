package com.yarosz.pong

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yarosz.pong.core.Difficulty
import com.yarosz.pong.core.Side
import com.yarosz.pong.screen.MenuRow
import com.yarosz.pong.screen.Mode
import com.yarosz.pong.screen.Shell
import com.yarosz.pong.screen.TitleRow

private val GUTTER = 16.dp
private val ROW_PITCH = 56.dp
/** Wide enough that the Paddles (x 48-72 and 1008-1032 px) show beside the panel. */
private val PANEL_INSET = 32.dp
private const val MARKER_PX = 32f
private const val MARKER_GAP_PX = 24f
private const val PANEL_BORDER_PX = 4f

private fun Difficulty.label() = when (this) {
    Difficulty.EASY -> "Easy"
    Difficulty.NORMAL -> "Normal"
    Difficulty.HARD -> "Hard"
}

@Composable
private fun Label(text: String, size: TextUnit, modifier: Modifier = Modifier) =
    BasicText(text, modifier, style = TextStyle(color = Color.White, fontSize = size, textAlign = TextAlign.Center))

/** One menu row: the 32 px square marker 24 px left of the label when selected (DESIGN.md item 25). */
@Composable
private fun MenuItem(label: String, selected: Boolean, onTap: () -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier.height(ROW_PITCH).pointerInput(label) { detectTapGestures { onTap() } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        with(density) {
            Box(Modifier.size(MARKER_PX.toDp()).background(if (selected) Color.White else Color.Transparent))
            Spacer(Modifier.width(MARKER_GAP_PX.toDp()))
        }
        Label(label, 28.sp)
    }
}

@Composable
fun Title(shell: Shell, onTap: (TitleRow) -> Unit) {
    val sound = if (shell.settings.soundOn) "Sound on" else "Sound off"
    val hint = when {
        shell.titleRow != TitleRow.SOUND -> "Turn the wheel to choose. Press it to start."
        shell.settings.soundOn -> "Press the wheel to turn sound off."
        else -> "Press the wheel to turn sound on."
    }
    Box(Modifier.fillMaxSize().padding(horizontal = GUTTER)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("Pong", 48.sp)
            Spacer(Modifier.height(24.dp))
            Column {
                for (row in TitleRow.entries) {
                    if (row == TitleRow.SOUND) Spacer(Modifier.height(24.dp))
                    MenuItem(row.difficulty?.label() ?: sound, shell.titleRow == row) { onTap(row) }
                }
            }
        }
        Label(hint, 16.sp, Modifier.align(Alignment.BottomCenter).padding(bottom = GUTTER))
    }
}

/** The Paused and Match over panel over the frozen field (DESIGN.md items 27-28). */
@Composable
fun Panel(shell: Shell, onTap: (MenuRow) -> Unit) {
    val heading = when {
        shell.mode == Mode.PAUSED -> "Paused"
        shell.winner == Side.PLAYER -> "You win"
        else -> "Computer wins"
    }
    val first = if (shell.mode == Mode.PAUSED) "Resume" else "Play again"
    val border = with(LocalDensity.current) { PANEL_BORDER_PX.toDp() }
    Box(Modifier.fillMaxSize().padding(horizontal = PANEL_INSET), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().background(Color.Black).border(border, Color.White).padding(GUTTER),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(heading, 32.sp)
            shell.difficulty?.let { Label(it.label(), 16.sp) }
            Column {
                MenuItem(first, shell.menuRow == MenuRow.FIRST) { onTap(MenuRow.FIRST) }
                MenuItem("Menu", shell.menuRow == MenuRow.MENU) { onTap(MenuRow.MENU) }
            }
            Label("Turn the wheel to choose. Press it to select.", 16.sp)
        }
    }
}

@Composable
fun ServePrompt(modifier: Modifier) =
    Label("Press the wheel to serve", 16.sp, modifier.background(Color.Black).padding(horizontal = 8.dp))
