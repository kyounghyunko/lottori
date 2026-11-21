package com.ycgc.lottori.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private fun Field.vector(): Vec2 {
    val data = items()
    if (data.size !in 2..3) invalid("Expected two or three coordinates.")
    data.getOrNull(2)?.number()
    return Vec2(data[0].number(), data[1].number())
}

private fun Field.scalar(): Double {
    if (value !is JsonArray) return number()
    return items().singleOrNull()?.number() ?: invalid("Expected a single numeric keyframe value.")
}

private fun Field.color(): Rgba {
    val data = items()
    if (data.size !in 3..4) invalid("Expected three or four color components.")
    val channels = data.map { channel ->
        channel.number().takeIf { it in 0.0..1.0 } ?: channel.invalid("Expected a color channel between 0 and 1.")
    }
    return Rgba(channels[0], channels[1], channels[2], channels.getOrElse(3) { 1.0 })
}

private fun Field.bezierPath(): BezierPath {
    val data = if (value is JsonArray) items().singleOrNull() ?: invalid("Expected one path.") else this
    fun points(name: String): List<Vec2> = data.require(name).items().map { it.vector() }
    val vertices = points("v")
    fun tangents(name: String): List<Vec2> = points(name).also {
        if (it.size != vertices.size) data.require(name).invalid("Each vertex needs one tangent.")
    }
    return BezierPath(vertices, tangents("i"), tangents("o"), data.get("c")?.bool() ?: false)
}

private fun Field.handle(): List<Double> {
    val values = if (value is JsonArray) items().map { it.number() } else listOf(number())
    return values.takeIf { it.isNotEmpty() } ?: invalid("Empty easing handle.")
}

private fun easing(key: Field): List<CubicEase> {
    val outgoing = key.get("o")
    val incoming = key.get("i")
    if (outgoing == null && incoming == null) return emptyList()
    val handles = listOf(outgoing ?: key.require("o"), incoming ?: key.require("i")).flatMap { handle ->
        val x = handle.require("x")
        val times = x.handle()
        if (times.any { it !in 0.0..1.0 }) x.invalid("Easing time must be between 0 and 1.")
        listOf(times, handle.require("y").handle())
    }
    val dimensions = handles.maxOf { it.size }
    if (handles.any { it.size != 1 && it.size != dimensions }) key.invalid("Inconsistent easing dimensions.")
    return List(dimensions) { i ->
        fun component(j: Int): Double = handles[j].getOrElse(i) { handles[j][0] }
        CubicEase(component(0), component(1), component(2), component(3))
    }
}

private fun <T : Any> LottieReader.track(property: Field, unsupportedAnimation: String? = null, decode: (Field) -> T): Track<T> {
    property.get("x")?.let {
        unsupported(it, "Expression", "Property uses an expression.")
        throw UnsupportedProperty()
    }
    val k = property.require("k")
    val animated = property.get("a")?.flag() ?: ((k.value as? JsonArray)?.firstOrNull() as? JsonObject)?.containsKey("t") ?: false
    if (!animated) return Track.Fixed(decode(k))
    if (unsupportedAnimation != null) {
        unsupported(property, unsupportedAnimation, "Animation of this property is unsupported.")
        throw UnsupportedProperty()
    }
    val nodes = k.items()
    if (nodes.isEmpty()) k.invalid("Animated property needs at least one keyframe.")
    val keys = mutableListOf<Key<T>>()
    var legacyEnd: T? = null
    for ((index, key) in nodes.withIndex()) {
        val t = key.require("t")
        val time = t.number()
        if (keys.isNotEmpty() && time < keys.last().time) t.invalid("Keyframe times must be in ascending order.")
        if (keys.size >= 2 && keys[keys.lastIndex - 1].time == time) t.invalid("At most two keys can share a time.")
        val hold = key.get("h")?.flag() == true
        val terminalFallback = if (index == nodes.lastIndex) legacyEnd ?: keys.lastOrNull()?.takeIf { it.hold }?.value else null
        val value = key.get("s")?.let(decode) ?: terminalFallback
            ?: throw InvalidField(key.copy(path = "${key.path}.s"), "Missing keyframe value.")
        for (name in listOf("to", "ti")) {
            key.get(name)?.takeIf { f -> f.items().any { it.number() != 0.0 } }?.let {
                unsupported(it, "SpatialTangent", "Position uses spatial interpolation.")
            }
        }
        keys += Key(time, value, hold, easing(key))
        legacyEnd = key.get("e")?.let(decode)
    }
    return Track.Animated(keys)
}

internal fun LottieReader.scalar(field: Field): Track<Double> = track(field) { it.scalar() }
internal fun LottieReader.vector(field: Field): Track<Vec2> = track(field) { it.vector() }
internal fun LottieReader.color(field: Field): Track<Rgba> = track(field, "AnimatedColor") { it.color() }
internal fun LottieReader.path(field: Field): Track<BezierPath> = track(field, "PathMorph") { it.bezierPath() }

internal fun LottieReader.transform(field: Field): Transform {
    val defaults = Transform()
    for (name in listOf("sk", "rx", "ry", "or")) {
        field.get(name)?.let {
            val nonzero = if (name == "or") {
                val k = it.get("k")?.value
                it.get("a")?.flag() == true || k !is JsonArray || it.require("k").items().any { n -> n.number() != 0.0 }
            } else scalar(it) != Track.Fixed(0.0)
            if (nonzero) unsupported(it, if (name == "sk") "Skew" else "Layer3D", "Unsupported transform component $name.")
        }
    }
    val position = field.get("p")?.let {
        if (it.get("s")?.bool() == true) {
            unsupported(it, "SplitPosition", "Position uses separate coordinate tracks.")
            defaults.position
        } else vector(it)
    } ?: defaults.position
    return Transform(
        anchor = field.get("a")?.let { vector(it) } ?: defaults.anchor,
        position = position,
        scale = field.get("s")?.let { vector(it) } ?: defaults.scale,
        rotation = (field.get("r") ?: field.get("rz"))?.let { scalar(it) } ?: defaults.rotation,
        opacity = field.get("o")?.let { scalar(it) } ?: defaults.opacity,
    )
}
