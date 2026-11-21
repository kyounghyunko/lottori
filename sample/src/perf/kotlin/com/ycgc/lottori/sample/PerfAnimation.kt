package com.ycgc.lottori.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import com.ycgc.lottori.LottoriAnimation
import com.ycgc.lottori.core.decodeJson
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

internal fun interface PerfAnimation {
    @Composable fun Draw(progress: () -> Float, modifier: Modifier)
}

internal fun decodeAnimation(engine: String, bytes: ByteArray): PerfAnimation = when (engine) {
    "lottori" -> {
        val result = decodeJson(bytes)
        val composition = checkNotNull(result.composition) { result.diagnostics.toString() }
        PerfAnimation { progress, modifier -> LottoriAnimation(composition, progress, modifier) }
    }
    "compottie-lite" -> {
        val composition = LottieComposition.parse(bytes.decodeToString())
        PerfAnimation { progress, modifier ->
            val painter = rememberLottiePainter(composition, progress)
            Box(modifier.paint(painter, contentScale = ContentScale.Fit))
        }
    }
    else -> error("Unknown benchmark engine: $engine")
}
