package com.ycgc.lottori

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.ycgc.lottori.core.LottoriComposition

/** Returns progress between 0 and 1. Pausing preserves elapsed time; changing composition or iterations restarts playback. */
@Composable
fun rememberLottoriPlayback(
    composition: LottoriComposition,
    iterations: Int = 1,
    isPlaying: Boolean = true,
    speed: Float = 1f,
): State<Float> {
    require(iterations > 0) { "iterations must be positive" }
    require(speed.isFinite() && speed > 0f) { "speed must be finite and positive" }
    val cycles = remember(composition, iterations) { mutableDoubleStateOf(0.0) }
    val progress = remember(cycles) {
        derivedStateOf { if (cycles.doubleValue >= iterations) 1f else (cycles.doubleValue % 1.0).toFloat() }
    }
    LaunchedEffect(composition, iterations, isPlaying, speed) {
        val startCycles = cycles.doubleValue
        if (!isPlaying || startCycles >= iterations) return@LaunchedEffect
        val startTime = withFrameNanos { it }
        while (cycles.doubleValue < iterations) {
            val now = withFrameNanos { it }
            val elapsedSeconds = (now - startTime) / 1_000_000_000.0
            val elapsedCycles = startCycles + elapsedSeconds * speed / composition.durationSeconds
            cycles.doubleValue = maxOf(cycles.doubleValue, elapsedCycles)
        }
    }
    return progress
}
