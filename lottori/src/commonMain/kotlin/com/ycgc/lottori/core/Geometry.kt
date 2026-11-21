package com.ycgc.lottori.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ELLIPSE_TANGENT = 0.5519150244935106
private val zero = Vec2(0.0, 0.0)

internal fun Transform.matrixAt(frame: Double): Affine {
    val p = position.vectorAt(frame)
    val s = scale.vectorAt(frame) * 0.01
    val a = anchor.vectorAt(frame)
    val angle = rotation.scalarAt(frame) * PI / 180.0
    val c = cos(angle)
    val r = sin(angle)
    val matrix = Affine(c * s.x, r * s.x, -r * s.y, c * s.y)
    val offset = p - matrix.vector(a)
    return matrix.copy(tx = offset.x, ty = offset.y)
}

internal fun Shape.Ellipse.contour(frame: Double): BezierPath {
    val center = position.vectorAt(frame)
    val (x, y) = size.vectorAt(frame) * 0.5
    val vertices = listOf(Vec2(0.0, -y), Vec2(x, 0.0), Vec2(0.0, y), Vec2(-x, 0.0))
    val outgoing = listOf(Vec2(x, 0.0), Vec2(0.0, y), Vec2(-x, 0.0), Vec2(0.0, -y)).map { it * ELLIPSE_TANGENT }
    return BezierPath(vertices.map { center + it }, outgoing.map { it * -1.0 }, outgoing, true).withDirection(reversed)
}

internal fun Shape.Rect.contour(frame: Double): BezierPath {
    val (x, y) = position.vectorAt(frame)
    val (w, h) = size.vectorAt(frame) * 0.5
    val left = x - w
    val right = x + w
    val top = y - h
    val bottom = y + h
    val r = minOf(radius.scalarAt(frame), w, h)
    if (r <= 0.0) {
        val corners = listOf(Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom), Vec2(left, top))
        val tangents = List(corners.size) { zero }
        return BezierPath(corners, tangents, tangents, true).withDirection(reversed)
    }
    val t = ELLIPSE_TANGENT * r
    val vertices = listOf(
        Vec2(right, top + r), Vec2(right, bottom - r), Vec2(right - r, bottom), Vec2(left + r, bottom),
        Vec2(left, bottom - r), Vec2(left, top + r), Vec2(left + r, top), Vec2(right - r, top),
    )
    val incoming = listOf(Vec2(0.0, -t), zero, Vec2(t, 0.0), zero, Vec2(0.0, t), zero, Vec2(-t, 0.0), zero)
    val outgoing = listOf(zero, Vec2(0.0, t), zero, Vec2(-t, 0.0), zero, Vec2(0.0, -t), zero, Vec2(t, 0.0))
    return BezierPath(vertices, incoming, outgoing, true).withDirection(reversed)
}

private fun BezierPath.withDirection(reversed: Boolean): BezierPath {
    if (!reversed || vertices.size < 2) return this
    fun flip(points: List<Vec2>) = listOf(points.first()) + points.drop(1).asReversed()
    return BezierPath(flip(vertices), flip(outTangents), flip(inTangents), closed)
}

internal fun BezierPath.transformed(matrix: Affine) =
    BezierPath(vertices.map(matrix::map), inTangents.map(matrix::vector), outTangents.map(matrix::vector), closed)
