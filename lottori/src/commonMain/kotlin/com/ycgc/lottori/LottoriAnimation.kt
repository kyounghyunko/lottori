package com.ycgc.lottori

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ycgc.lottori.core.LottoriComposition

/** Draws the frame selected by [progress]. A null description marks a decorative animation. */
@Composable
fun LottoriAnimation(
    composition: LottoriComposition,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val buffers = remember { DrawingBuffers() }
    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Canvas(modifier.then(semantics).size(composition.width.dp, composition.height.dp)) {
        drawLottori(composition, progress().toDouble(), contentScale, buffers)
    }
}

/** Plays [iterations] times and holds the last visible frame after the final iteration. */
@Composable
fun LottoriAnimation(
    composition: LottoriComposition,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    iterations: Int = 1,
    speed: Float = 1f,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val progress = rememberLottoriPlayback(composition, iterations, isPlaying, speed)
    LottoriAnimation(composition, { progress.value }, modifier, contentDescription, contentScale)
}
