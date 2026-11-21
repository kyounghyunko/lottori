package com.ycgc.lottori

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ycgc.lottori.core.decodeJson
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

internal expect val goldenPlatform: String

class GoldenFrameTest {
    @Test
    fun trimPathReportsTheNamedLayerAndOperatorLocation() {
        val parsed = decodeJson(GoldenAssets.inputs.getValue("trim-path"))
        assertEquals(null, parsed.composition)
        val diagnostic = parsed.diagnostics.single()
        assertEquals("TrimPath", diagnostic.code)
        assertEquals("$.layers[0].shapes[0].it[1]", diagnostic.path)
        assertEquals("checkmark", diagnostic.layerName)
    }

    @Test
    fun appFramesMatchThePublishedReferences() {
        for ((key, reference) in GoldenAssets.frames) {
            val source = key.substringBefore('/', "")
            if (source in setOf("android", "ios") && source != goldenPlatform) continue
            val asset = key.substringAfterLast('/').substringBeforeLast('-')
            val progress = key.substringAfterLast('-').toInt() / 100.0
            val parsed = decodeJson(GoldenAssets.inputs.getValue(asset))
            val composition = assertNotNull(parsed.composition, parsed.diagnostics.toString())
            val width = composition.width
            val height = composition.height
            assertEquals(width, reference.width)
            assertEquals(height, reference.height)
            val bitmap = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                drawLottori(composition, progress)
            }
            val actual = bitmap.toPixelMap()
            fun pixels() = buildString {
                var previous = actual[0, 0].toArgb()
                var run = 0
                fun flush() { append("$run:${previous.toUInt().toString(16)},") }
                for (i in 0 until width * height) {
                    val value = actual[i % width, i / width].toArgb()
                    if (value != previous) { flush(); previous = value; run = 0 }
                    run++
                }
                flush()
            }
            if (GoldenAssets.capture && source == "web") {
                val chunks = pixels().chunked(2000)
                chunks.forEachIndexed { index, data ->
                    println("LOTTORI_PART $goldenPlatform $key $index ${chunks.size} $data END_PART")
                }
            }
            for (y in 0 until height) for (x in 0 until width) {
                val pixel = reference.pixel(x, y)
                val observed = actual[x, y].toArgb()
                val edge = (maxOf(0, y-1)..minOf(height-1, y+1)).any { row ->
                    (maxOf(0, x-1)..minOf(width-1, x+1)).any { column -> reference.pixel(column, row) != pixel }
                }
                val tolerance = when {
                    !edge -> 3
                    source == goldenPlatform -> 8
                    else -> 41
                }
                if (channelError(observed, pixel) > tolerance) {
                    fail("Frame $key at ($x,$y), expected ${pixel.toUInt().toString(16)}, got ${observed.toUInt().toString(16)}. LOTTORI_FRAME $key ${pixels()} END_FRAME")
                }
            }
        }
    }

    private fun channelError(left: Int, right: Int): Int {
        val a = left ushr 24
        val b = right ushr 24
        var error = abs(a - b)
        for (shift in listOf(16, 8, 0)) {
            val l = ((left ushr shift) and 255) * a
            val r = ((right ushr shift) and 255) * b
            error = maxOf(error, (abs(l - r) + 127) / 255)
        }
        return error
    }
}
