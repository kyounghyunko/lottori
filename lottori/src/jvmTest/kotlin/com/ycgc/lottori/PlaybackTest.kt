package com.ycgc.lottori

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.ycgc.lottori.core.decodeJson
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalComposeUiApi::class)
class PlaybackTest {
    private val composition = assertNotNull(decodeJson(GoldenAssets.json.encodeToByteArray()).composition)

    @Test
    fun playerFollowsTheFrameClockThroughTwoIterations() {
        val scene = ImageComposeScene(width = 100, height = 40, density = Density(1f)) {
            LottoriAnimation(composition, iterations = 2)
        }
        try {
            scene.render(0L).close()
            for ((millis, x) in listOf(0 to 20, 500 to 50, 350 to 50, 1000 to 20, 2000 to 78, 2500 to 78)) {
                scene.assertCenter(millis, x)
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun resumingExcludesTimeSpentPaused() {
        val playing = mutableStateOf(true)
        val scene = ImageComposeScene(width = 100, height = 40, density = Density(1f)) {
            val progress = rememberLottoriPlayback(composition, isPlaying = playing.value)
            LottoriAnimation(composition, { progress.value })
        }
        try {
            scene.render(0L).close()
            scene.assertCenter(0, 20)
            scene.assertCenter(250, 35)
            playing.value = false
            scene.assertCenter(250, 35)
            scene.assertCenter(1500, 35)
            playing.value = true
            scene.assertCenter(1500, 35)
            scene.assertCenter(1500, 35)
            scene.assertCenter(1750, 50)
            scene.assertCenter(2250, 78)
        } finally {
            scene.close()
        }
    }

    @Test
    fun externalProgressRedrawsWithoutRecomposingTheCaller() {
        val progress = mutableFloatStateOf(0f)
        var compositions = 0
        val scene = ImageComposeScene(100, 40, Density(1f)) {
            SideEffect { compositions++ }
            LottoriAnimation(composition, { progress.floatValue })
        }
        try {
            scene.assertCenter(0, 20)
            val initialCompositions = compositions
            for ((value, expected) in listOf(1.2f to 78, 0.5f to 50, -1f to 20)) {
                progress.floatValue = value
                scene.assertCenter(500, expected)
            }
            assertEquals(initialCompositions, compositions)
        } finally {
            scene.close()
        }
    }

    @Test
    fun onlyLabeledAnimationsExposeImageSemantics() {
        val label = mutableStateOf<String?>("Sync")
        val scene = ImageComposeScene(width = 100, height = 40, density = Density(1f)) {
            LottoriAnimation(composition, { 0.5f }, contentDescription = label.value)
        }
        try {
            scene.render().close()
            val root = scene.semanticsOwners.single().rootSemanticsNode
            val node = root.children.single()
            assertEquals(listOf("Sync"), node.config[SemanticsProperties.ContentDescription])
            assertEquals(Role.Image, node.config[SemanticsProperties.Role])
            label.value = null
            scene.render().close()
            assertEquals(emptyList(), root.children.mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription) })
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.assertCenter(millis: Int, x: Int) {
        render(millis * 1_000_000L).use { image ->
            Bitmap.makeFromImage(image).use { bitmap ->
                val coverage = (0 until bitmap.width).filter { bitmap.getColor(it, 20).ushr(24) >= 128 }
                assertEquals((x - 6 until x + 6).toList(), coverage, "Circle at ${millis}ms")
                assertEquals(0xffff0000.toInt(), bitmap.getColor(x, 20))
            }
        }
    }
}
