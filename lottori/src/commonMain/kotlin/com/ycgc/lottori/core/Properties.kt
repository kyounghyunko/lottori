package com.ycgc.lottori.core

private fun mix(from: Double, to: Double, fraction: Double) = from + (to - from) * fraction

private fun CubicEase.fractionAt(time: Double): Double {
    if (time == 0.0 || time == 1.0 || (x1 == y1 && x2 == y2)) return time
    fun cubic(t: Double, p1: Double, p2: Double): Double {
        val u = 1.0 - t
        return 3.0 * u * u * t * p1 + 3.0 * u * t * t * p2 + t * t * t
    }
    var low = 0.0
    var high = 1.0
    repeat(28) {
        val t = (low + high) / 2.0
        if (cubic(t, x1, x2) < time) low = t else high = t
    }
    return cubic((low + high) / 2.0, y1, y2)
}

private class Interval(val time: Double, val easing: List<CubicEase>) {
    fun fraction(channel: Int): Double =
        (easing.getOrNull(channel) ?: easing.firstOrNull())?.fractionAt(time) ?: time
}

private inline fun <T> Track<T>.sample(frame: Double, blend: (T, T, Interval) -> T): T {
    when (this) {
        is Track.Fixed -> return value
        is Track.Animated -> {
            if (frame < keys.first().time) return keys.first().value
            var low = 0
            var high = keys.size
            while (low < high) {
                val mid = low + (high - low) / 2
                if (keys[mid].time <= frame) low = mid + 1 else high = mid
            }
            val start = keys[low - 1]
            val next = keys.getOrNull(low) ?: return start.value
            if (start.hold || frame == start.time) return start.value
            val time = (frame - start.time) / (next.time - start.time)
            return blend(start.value, start.endValue ?: next.value, Interval(time, start.easing))
        }
    }
}

internal fun Track<Double>.scalarAt(frame: Double) = sample(frame) { a, b, interval ->
    mix(a, b, interval.fraction(0))
}

internal fun Track<Vec2>.vectorAt(frame: Double) = sample(frame) { a, b, interval ->
    Vec2(mix(a.x, b.x, interval.fraction(0)), mix(a.y, b.y, interval.fraction(1)))
}

internal fun Track<Rgba>.colorAt(frame: Double) = sample(frame) { a, b, interval ->
    Rgba(
        mix(a.r, b.r, interval.fraction(0)), mix(a.g, b.g, interval.fraction(1)),
        mix(a.b, b.b, interval.fraction(2)), mix(a.a, b.a, interval.fraction(3)),
    )
}

internal fun Track<BezierPath>.pathAt(frame: Double) = sample(frame) { a, b, interval ->
    val t = interval.fraction(0)
    fun points(from: List<Vec2>, to: List<Vec2>) = from.mapIndexed { index, start ->
        start + (to[index] - start) * t
    }
    BezierPath(points(a.vertices, b.vertices), points(a.inTangents, b.inTangents), points(a.outTangents, b.outTangents), a.closed)
}
