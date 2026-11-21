package com.ycgc.lottori.core

internal fun LottieReader.shapeGroup(array: Field, depth: Int): Shape.Group {
    if (depth > 128) array.invalid("Shape group nesting exceeds 128 levels.")
    var scope = Transform()
    var transformNode: Field? = null
    var lastPartialGroup: Field? = null
    val items = mutableListOf<Shape>()
    for (node in array.items()) {
        if (node.get("hd")?.bool() == true) continue
        val type = node.require("ty")
        fun vector(name: String) = vector(node.require(name))
        fun opacity() = node.get("o")?.let { scalar(it) } ?: Track.Fixed(100.0)
        fun reversed() = when (node.get("d")?.integer() ?: 1) {
            1 -> false
            3 -> true
            else -> node.require("d").invalid("Unknown shape direction.")
        }
        node.get("bm")?.takeIf { it.integer() != 0 }?.let {
            unsupported(it, "BlendMode", "Shape uses a blend mode.")
        }
        when (val name = type.string()) {
            "gr" -> {
                val group = shapeGroup(node.require("it"), depth + 1)
                if (group.hasPartialOpacity()) lastPartialGroup = node
                items += group
            }
            "tr" -> {
                if (transformNode != null) type.invalid("Only one transform per group is supported.")
                transformNode = node
                scope = transform(node)
            }
            "rc" -> items += Shape.Rect(vector("p"), vector("s"), node.get("r")?.let { scalar(it) } ?: Track.Fixed(0.0), reversed())
            "el" -> items += Shape.Ellipse(vector("p"), vector("s"), reversed())
            "sh" -> {
                if (reversed()) unsupported(node.require("d"), "PathDirection", "Path specifies reversed vertex order.")
                items += Shape.Path(path(node.require("ks")))
            }
            "fl", "st" -> {
                if (node.get("fillEnabled")?.bool() == false) continue
                lastPartialGroup?.let {
                    unsupported(it, "InheritedPaintOpacity", "Paint crosses a translucent group boundary.")
                    lastPartialGroup = null
                }
                val paintColor = color(node.require("c"))
                if (name == "fl") {
                    val rule = when (node.get("r")?.integer() ?: 1) {
                        1 -> FillRule.NonZero
                        2 -> FillRule.EvenOdd
                        else -> node.require("r").invalid("Unknown fill rule.")
                    }
                    items += Shape.Fill(paintColor, opacity(), rule)
                } else {
                    node.get("d")?.takeIf { it.items().isNotEmpty() }?.let {
                        unsupported(it, "Dash", "Stroke uses a dash pattern.")
                    }
                    val cap = node.get("lc")?.let { LineCap.entries.getOrNull(it.integer() - 1) ?: it.invalid("Unknown line cap.") } ?: LineCap.Butt
                    val join = node.get("lj")?.let { LineJoin.entries.getOrNull(it.integer() - 1) ?: it.invalid("Unknown line join.") } ?: LineJoin.Miter
                    val miter = node.get("ml")?.number() ?: 4.0
                    node.get("ml2")?.let { unsupported(it, "AnimatedMiter", "Stroke animates the miter limit.") }
                    items += Shape.Stroke(paintColor, opacity(), scalar(node.require("w")), cap, join, miter)
                }
            }
            else -> {
                val code = when (name) {
                    "tm" -> "TrimPath"
                    "rp" -> "Repeater"
                    "sr" -> "Star"
                    "gf", "gs" -> "Gradient"
                    else -> "UnknownShape"
                }
                unsupported(node, code, "Unsupported shape or operator '$name'.")
            }
        }
    }
    return Shape.Group(items, scope)
}

private fun Shape.Group.hasPartialOpacity(): Boolean =
    transform.opacity != Track.Fixed(100.0) || items.any { it is Shape.Group && it.hasPartialOpacity() }
