package com.ycgc.lottori.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ycgc.lottori.LottoriAnimation
import com.ycgc.lottori.LottoriLoadState
import com.ycgc.lottori.rememberLottoriComposition

@Composable
internal fun SampleScreen(readBytes: suspend (String) -> ByteArray) {
    Box(Modifier.fillMaxSize()) {
        val state = rememberLottoriComposition("input") {
            runCatching { readBytes("files/input.json") }.getOrElse { readBytes("files/input.lottie") }
        }
        if (state is LottoriLoadState.Ready) LottoriAnimation(state.composition)
    }
}
