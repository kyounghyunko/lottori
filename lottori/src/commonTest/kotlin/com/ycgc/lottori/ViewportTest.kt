package com.ycgc.lottori

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ycgc.lottori.core.decodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ViewportTest {
    @Test
    fun fillBoundsStretchesTheSourceToBothViewportEdges() {
        val composition = assertNotNull(decodeJson(GoldenAssets.json.encodeToByteArray()).composition)
        val image = ImageBitmap(100, 80)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(100f, 80f)) {
            drawLottori(composition, 0.5, ContentScale.FillBounds)
        }
        val pixels = image.toPixelMap()
        assertEquals(1f, pixels[50, 30].red, 0.01f)
        assertEquals(1f, pixels[50, 50].alpha, 0.01f)
        assertEquals(0f, pixels[50, 53].alpha)
        assertEquals(0f, pixels[57, 40].alpha)
    }

    @Test
    fun rotatedOverlappingShapesShareLayerOpacityAndClipToFittedBounds() {
        val group = """{"ty":"gr","it":[
            {"ty":"rc","p":{"k":[6.5,0]},"s":{"k":[17,4]},"r":{"k":0}},
            {"ty":"fl","c":{"k":[0,1,0,1]},"o":{"k":100}}
        ]}"""
        val parsed = decodeJson("""{
            "w":40,"h":20,"fr":30,"ip":0,"op":30,
            "layers":[{"ty":4,"ind":1,"ip":0,"op":30,
                "ks":{"p":{"k":[20,10]},"r":{"k":90},"o":{"k":50}},
                "shapes":[$group,$group]}]
        }""".encodeToByteArray())
        val composition = assertNotNull(parsed.composition, parsed.diagnostics.toString())
        val image = ImageBitmap(80, 80)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(80f, 80f)) {
            drawLottori(composition, 0.5)
        }
        val pixels = image.toPixelMap()
        assertEquals(1f, pixels[40, 45].green, 0.01f)
        assertEquals(0.5f, pixels[40, 45].alpha, 0.01f)
        assertEquals(0f, pixels[40, 30].alpha)
        assertEquals(0f, pixels[45, 45].alpha)
        assertEquals(0.5f, pixels[40, 59].alpha, 0.01f)
        assertEquals(0f, pixels[40, 60].alpha)
    }
}
