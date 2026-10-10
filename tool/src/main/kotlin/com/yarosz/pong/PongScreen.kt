package com.yarosz.pong

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant

class PongViewModel : LightViewModel<Unit>()

@InitialScreen
class PongScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, PongViewModel>(sealedActivity) {

    override val viewModelClass: Class<PongViewModel>
        get() = PongViewModel::class.java

    override fun createViewModel() = PongViewModel()

    @Composable
    override fun Content() {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            LightText(text = "Pong", variant = LightTextVariant.Heading, color = Color.White)
        }
    }
}
