package com.ycgc.lottori

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint as LayerPaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.ycgc.lottori.core.Affine
import com.ycgc.lottori.core.BezierPath
import com.ycgc.lottori.core.DrawPath
import com.ycgc.lottori.core.FillRule
import com.ycgc.lottori.core.LineCap
import com.ycgc.lottori.core.LineJoin
import com.ycgc.lottori.core.LottoriComposition
import com.ycgc.lottori.core.OpacityGroup
import com.ycgc.lottori.core.Paint

internal class DrawingBuffers {
    val path = Path()
    val matrix = Matrix()
    val layerPaint = LayerPaint()
    val opacityStack = mutableListOf<OpacityGroup>()
}

internal fun DrawScope.drawLottori(
    composition: LottoriComposition,
    progress: Double,
    contentScale: ContentScale = ContentScale.Fit,
    buffers: DrawingBuffers = DrawingBuffers(),
) {
    if (size.isEmpty()) return
    val width = composition.width.toFloat()
    val height = composition.height.toFloat()
    val factor = contentScale.computeScaleFactor(Size(width, height), size)
    withTransform({
        clipRect()
        translate((size.width - width * factor.scaleX) / 2f, (size.height - height * factor.scaleY) / 2f)
        scale(factor.scaleX, factor.scaleY, Offset.Zero)
        clipRect(0f, 0f, width, height)
    }) {
        val frame = composition.frameAtProgress(progress)
        drawItems(composition.evaluate(frame), Rect(0f, 0f, width, height), buffers)
    }
}

private fun DrawScope.drawItems(items: List<DrawPath>, bounds: Rect, buffers: DrawingBuffers) {
    val canvas = drawContext.canvas
    val active = buffers.opacityStack
    try {
        for (item in items) {
            val groups = item.opacityGroups
            var shared = 0
            while (shared < minOf(groups.size, active.size) && groups[shared] == active[shared]) shared++
            while (active.size > shared) {
                canvas.restore()
                active.removeAt(active.lastIndex)
            }
            for (index in shared until groups.size) {
                val group = groups[index]
                buffers.layerPaint.alpha = group.opacity.toFloat()
                canvas.saveLayer(bounds, buffers.layerPaint)
                active += group
            }
            drawItem(item, buffers)
        }
    } finally {
        repeat(active.size) { canvas.restore() }
        active.clear()
    }
}

private fun DrawScope.drawItem(item: DrawPath, buffers: DrawingBuffers) {
    val path = buffers.path
    path.reset()
    for (contour in item.contours) path.append(contour)
    val paint = item.paint
    val style = when (paint) {
        is Paint.Fill -> {
            path.fillType = when (paint.rule) {
                FillRule.NonZero -> PathFillType.NonZero
                FillRule.EvenOdd -> PathFillType.EvenOdd
            }
            Fill
        }
        is Paint.Stroke -> {
            if (paint.width <= 0.0) return
            Stroke(
                width = paint.width.toFloat(),
                miter = paint.miterLimit.toFloat(),
                cap = when (paint.cap) {
                    LineCap.Butt -> StrokeCap.Butt
                    LineCap.Round -> StrokeCap.Round
                    LineCap.Square -> StrokeCap.Square
                },
                join = when (paint.join) {
                    LineJoin.Miter -> StrokeJoin.Miter
                    LineJoin.Round -> StrokeJoin.Round
                    LineJoin.Bevel -> StrokeJoin.Bevel
                },
            )
        }
    }
    val rgba = paint.color
    val color = Color(
        rgba.r.toFloat().coerceIn(0f, 1f),
        rgba.g.toFloat().coerceIn(0f, 1f),
        rgba.b.toFloat().coerceIn(0f, 1f),
        (rgba.a * item.opacity).toFloat().coerceIn(0f, 1f),
    )
    withTransform({ transform(item.transform.writeTo(buffers.matrix)) }) {
        drawPath(path, color, style = style)
    }
}

private fun Path.append(contour: BezierPath) {
    val points = contour.vertices
    if (points.isEmpty()) return
    moveTo(points.first().x.toFloat(), points.first().y.toFloat())
    val segments = if (contour.closed) points.size else points.size - 1
    for (i in 0 until segments) {
        val next = (i + 1) % points.size
        val start = points[i]
        val end = points[next]
        val outgoing = contour.outTangents[i]
        val incoming = contour.inTangents[next]
        cubicTo(
            (start.x + outgoing.x).toFloat(), (start.y + outgoing.y).toFloat(),
            (end.x + incoming.x).toFloat(), (end.y + incoming.y).toFloat(),
            end.x.toFloat(), end.y.toFloat(),
        )
    }
    if (contour.closed) close()
}

private fun Affine.writeTo(target: Matrix) = target.also { matrix ->
    matrix[0, 0] = a.toFloat()
    matrix[0, 1] = b.toFloat()
    matrix[1, 0] = c.toFloat()
    matrix[1, 1] = d.toFloat()
    matrix[3, 0] = tx.toFloat()
    matrix[3, 1] = ty.toFloat()
}
