package com.ycgc.lottori.core

import kotlin.math.ceil

internal data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Vec2(x * scale, y * scale)
}

internal data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0)

internal data class BezierPath(
    val vertices: List<Vec2>,
    val inTangents: List<Vec2>,
    val outTangents: List<Vec2>,
    val closed: Boolean,
) {
    init {
        require(vertices.size == inTangents.size && vertices.size == outTangents.size)
    }
}

internal sealed interface Track<T> {
    data class Fixed<T>(val value: T) : Track<T>
    data class Animated<T>(val keys: List<Key<T>>) : Track<T> {
        init {
            require(keys.isNotEmpty() && keys.zipWithNext().all { (a, b) -> a.time <= b.time })
        }
    }
}

internal data class Key<T>(
    val time: Double,
    val value: T,
    val hold: Boolean = false,
    val easing: List<CubicEase> = emptyList(),
    val endValue: T? = null,
)

internal data class CubicEase(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

internal data class Transform(
    val anchor: Track<Vec2> = Track.Fixed(Vec2(0.0, 0.0)),
    val position: Track<Vec2> = Track.Fixed(Vec2(0.0, 0.0)),
    val scale: Track<Vec2> = Track.Fixed(Vec2(100.0, 100.0)),
    val rotation: Track<Double> = Track.Fixed(0.0),
    val opacity: Track<Double> = Track.Fixed(100.0),
)

internal enum class FillRule { NonZero, EvenOdd }
internal enum class LineCap { Butt, Round, Square }
internal enum class LineJoin { Miter, Round, Bevel }

internal sealed interface Shape {
    data class Rect(
        val position: Track<Vec2>, val size: Track<Vec2>,
        val radius: Track<Double> = Track.Fixed(0.0), val reversed: Boolean = false,
    ) : Shape

    data class Ellipse(val position: Track<Vec2>, val size: Track<Vec2>, val reversed: Boolean = false) : Shape
    data class Path(val path: Track<BezierPath>) : Shape
    data class Group(val items: List<Shape>, val transform: Transform = Transform()) : Shape
    data class Fill(
        val color: Track<Rgba>, val opacity: Track<Double> = Track.Fixed(100.0),
        val rule: FillRule = FillRule.NonZero,
    ) : Shape

    data class Stroke(
        val color: Track<Rgba>, val opacity: Track<Double>, val width: Track<Double>,
        val cap: LineCap = LineCap.Butt, val join: LineJoin = LineJoin.Miter, val miterLimit: Double = 4.0,
    ) : Shape
}

internal data class Layer(
    val id: Int?, val parentId: Int?, val inPoint: Double, val outPoint: Double,
    val transform: Transform, val shapes: List<Shape>,
)

class LottoriComposition internal constructor(
    val width: Int, val height: Int, val frameRate: Double, val inPoint: Double,
    val outPoint: Double, internal val layers: List<Layer>,
) {
    init {
        require(width > 0 && height > 0 && frameRate.isFinite() && frameRate > 0)
        require(inPoint.isFinite() && outPoint.isFinite() && inPoint < outPoint)
        require(durationSeconds.isFinite() && durationSeconds > 0.0)
    }

    val durationSeconds: Double get() = (outPoint - inPoint) / frameRate

    internal val layersById = layers.mapNotNull { layer -> layer.id?.let { it to layer } }.toMap()

    internal val renderLayers = layers.asReversed().filter { it.shapes.isNotEmpty() }.map { it to GroupPlan.compile(it.shapes) }

    fun frameAtProgress(progress: Double): Double {
        require(progress.isFinite())
        return (inPoint + progress.coerceIn(0.0, 1.0) * (outPoint - inPoint))
            .coerceIn(inPoint, maxOf(inPoint, ceil(outPoint) - 1.0))
    }

    internal fun evaluate(frame: Double): List<DrawPath> = evaluateComposition(this, frame)
}

internal data class Affine(
    val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0,
    val d: Double = 1.0, val tx: Double = 0.0, val ty: Double = 0.0,
) {
    fun map(point: Vec2) = vector(point) + Vec2(tx, ty)
    fun vector(point: Vec2) = Vec2(a * point.x + c * point.y, b * point.x + d * point.y)
    operator fun times(local: Affine) = Affine(
        a * local.a + c * local.b, b * local.a + d * local.b,
        a * local.c + c * local.d, b * local.c + d * local.d,
        a * local.tx + c * local.ty + tx, b * local.tx + d * local.ty + ty,
    )
}

internal sealed interface Paint {
    val color: Rgba
    data class Fill(override val color: Rgba, val rule: FillRule) : Paint
    data class Stroke(
        override val color: Rgba, val width: Double, val cap: LineCap, val join: LineJoin, val miterLimit: Double,
    ) : Paint
}

internal data class DrawPath(
    val contours: List<BezierPath>, val transform: Affine, val paint: Paint, val opacity: Double,
    val opacityGroups: List<OpacityGroup> = emptyList(),
)

internal data class OpacityGroup(val id: Int, val opacity: Double)
