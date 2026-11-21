package com.ycgc.lottori.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SampleScreen(readBytes: (String) -> ByteArray) {
    Box(Modifier.fillMaxSize())
}
