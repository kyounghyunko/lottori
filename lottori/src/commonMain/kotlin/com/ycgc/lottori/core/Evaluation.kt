package com.ycgc.lottori.core

internal sealed interface Outline {
    fun append(frame: Double, matrix: Affine?, to: MutableList<BezierPath>)
}

private class Geometry(val sample: (Double) -> BezierPath) : Outline {
    override fun append(frame: Double, matrix: Affine?, to: MutableList<BezierPath>) {
        val path = sample(frame)
        to.add(if (matrix == null) path else path.transformed(matrix))
    }
}

private data class Child(val group: GroupPlan, val transform: Transform) : Outline {
    override fun append(frame: Double, matrix: Affine?, to: MutableList<BezierPath>) {
        if (transform.opacity.scalarAt(frame) <= 0.0) return
        val local = transform.matrixAt(frame)
        val combined = matrix?.times(local) ?: local
        group.outlines.forEach { it.append(frame, combined, to) }
    }
}

internal class GroupPlan private constructor(
    val outlines: List<Outline>,
    val order: List<Command>,
) {
    sealed interface Command {
        data class Group(val plan: GroupPlan, val transform: Transform) : Command
        data class Style(val outlineCount: Int, val opacity: Track<Double>, val paint: (Double) -> Paint?) : Command
    }

    companion object {
        fun compile(shapes: List<Shape>): GroupPlan {
            val paths = mutableListOf<Outline>()
            val commands = mutableListOf<Command>()
            for (shape in shapes) {
                when (shape) {
                    is Shape.Rect -> paths += Geometry(shape::contour)
                    is Shape.Ellipse -> paths += Geometry(shape::contour)
                    is Shape.Path -> paths += Geometry(shape.path::pathAt)
                    is Shape.Group -> {
                        val child = compile(shape.items)
                        paths += Child(child, shape.transform)
                        commands += Command.Group(child, shape.transform)
                    }
                    is Shape.Fill -> commands += Command.Style(paths.size, shape.opacity) { frame ->
                        Paint.Fill(shape.color.colorAt(frame), shape.rule)
                    }
                    is Shape.Stroke -> commands += Command.Style(paths.size, shape.opacity) { frame ->
                        val width = shape.width.scalarAt(frame)
                        if (width <= 0.0) null else Paint.Stroke(shape.color.colorAt(frame), width, shape.cap, shape.join, shape.miterLimit)
                    }
                }
            }
            return GroupPlan(paths, commands.asReversed())
        }
    }
}

internal fun evaluateComposition(composition: LottoriComposition, frame: Double): List<DrawPath> {
    require(frame.isFinite())
    if (frame < composition.inPoint || frame >= composition.outPoint) return emptyList()
    return FrameEvaluator(composition, frame).draw()
}

private class FrameEvaluator(val composition: LottoriComposition, val frame: Double) {
    private val parentMatrices = mutableMapOf<Int, Affine>()
    private var nextGroupId = 0

    fun draw(): List<DrawPath> = buildList {
        for ((layer, plan) in composition.renderLayers) {
            if (frame < layer.inPoint || frame >= layer.outPoint) continue
            val opacity = layer.transform.opacity.scalarAt(frame).asAlpha()
            if (opacity == 0.0) continue
            val world = parentMatrix(layer.parentId) * layer.transform.matrixAt(frame)
            render(plan, world, opacityStack(emptyList(), opacity), this)
        }
    }

    private fun parentMatrix(id: Int?): Affine {
        var parentId = id
        val pending = mutableListOf<Layer>()
        while (parentId != null && parentId !in parentMatrices) {
            val parent = composition.layersById.getValue(parentId)
            pending.add(parent)
            parentId = parent.parentId
        }
        var matrix = parentId?.let(parentMatrices::getValue) ?: Affine()
        for (parent in pending.asReversed()) {
            matrix *= parent.transform.matrixAt(frame)
            parentMatrices[parent.id!!] = matrix
        }
        return matrix
    }

    private fun opacityStack(stack: List<OpacityGroup>, alpha: Double): List<OpacityGroup> =
        if (alpha == 1.0) stack else stack + OpacityGroup(nextGroupId++, alpha)

    private fun render(plan: GroupPlan, world: Affine, groups: List<OpacityGroup>, to: MutableList<DrawPath>) {
        for (cmd in plan.order) when (cmd) {
            is GroupPlan.Command.Group -> {
                val opacity = cmd.transform.opacity.scalarAt(frame).asAlpha()
                if (opacity > 0.0) render(cmd.plan, world * cmd.transform.matrixAt(frame), opacityStack(groups, opacity), to)
            }
            is GroupPlan.Command.Style -> {
                val alpha = cmd.opacity.scalarAt(frame).asAlpha()
                if (alpha <= 0.0 || cmd.outlineCount == 0) continue
                val paint = cmd.paint(frame) ?: continue
                val contours = buildList {
                    for (index in 0 until cmd.outlineCount) plan.outlines[index].append(frame, null, this)
                }
                if (contours.isNotEmpty()) to += DrawPath(contours, world, paint, alpha, groups)
            }
        }
    }
}

private fun Double.asAlpha() = (this / 100).coerceIn(0.0, 1.0)
