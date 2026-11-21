package com.ycgc.lottori.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineTest {
    private val origin = Vec2(0.0, 0.0)
    private val ellipse = Shape.Ellipse(Track.Fixed(origin), Track.Fixed(Vec2(20.0, 10.0)))
    private val red = Rgba(1.0, 0.0, 0.0)
    private val blue = Rgba(0.0, 0.0, 1.0)
    private val fill = Shape.Fill(Track.Fixed(red))

    private fun layer(
        shapes: List<Shape>, transform: Transform = Transform(), id: Int? = null, parent: Int? = null,
    ) = Layer(id, parent, 10.0, 40.0, transform, shapes)

    private fun composition(vararg layers: Layer) =
        LottoriComposition(100, 40, 30.0, 10.0, 40.0, layers.toList())

    @Test
    fun externalProgressUsesCompositionTimeIncludingItsStartOffset() {
        val movement = Track.Animated(listOf(Key(10.0, Vec2(20.0, 20.0)), Key(40.0, Vec2(80.0, 20.0))))
        val icon = composition(layer(listOf(ellipse, fill), Transform(position = movement)))
        assertEquals(1.0, icon.durationSeconds)
        val frame = icon.frameAtProgress(0.5)
        assertEquals(25.0, frame)
        assertEquals(Vec2(50.0, 20.0), icon.evaluate(frame).single().transform.map(origin))
        for ((progress, expectedX) in listOf(1.0 to 78.0, 0.99 to 78.0, -0.1 to 20.0, 0.25 to 35.0)) {
            assertEquals(expectedX, icon.evaluate(icon.frameAtProgress(progress)).single().transform.map(origin).x)
        }
        assertEquals(39.0, icon.frameAtProgress(1.0))
        assertEquals(10.5, LottoriComposition(1, 1, 30.0, 10.5, 10.9, emptyList()).frameAtProgress(1.0))
        assertEquals(11.0, LottoriComposition(1, 1, 30.0, 10.5, 11.8, emptyList()).frameAtProgress(1.0))
        assertTrue(icon.evaluate(icon.frameAtProgress(1.0)).isNotEmpty())
        assertTrue(icon.evaluate(40.0).isEmpty())
    }

    @Test
    fun duplicateKeyTimeAndBackwardSeeksAreDeterministic() {
        val track = Track.Animated(listOf(
            Key(10.0, Vec2(2.0, 0.0), hold = true),
            Key(17.0, Vec2(4.0, 0.0)),
            Key(17.0, Vec2(10.0, 0.0)),
            Key(27.0, Vec2(30.0, 0.0)),
        ))
        val icon = composition(layer(listOf(ellipse, fill), Transform(position = track)))
        for ((frame, position) in listOf(22.0 to 20.0, 17.0 to 10.0, 16.99 to 2.0, 22.0 to 20.0)) {
            assertEquals(position, icon.evaluate(frame).single().transform.map(origin).x)
        }
    }

    @Test
    fun parentContributesMatrixEvenOutsideItsVisibilityButDoesNotFadeChild() {
        val parent = layer(emptyList(), Transform(position = Track.Fixed(Vec2(10.0, 10.0)),
            rotation = Track.Fixed(90.0), opacity = Track.Fixed(0.0)), id = 42).copy(outPoint = 12.0)
        val local = Transform(anchor = Track.Fixed(Vec2(3.0, 0.0)), position = Track.Fixed(Vec2(7.0, 0.0)),
            scale = Track.Fixed(Vec2(200.0, 100.0)))
        val painted = composition(layer(listOf(ellipse, fill), local, id = 5, parent = 42), parent)
            .evaluate(25.0).single()
        val p = painted.transform.map(Vec2(4.0, 0.0))
        assertEquals(10.0, p.x, 0.000001)
        assertEquals(19.0, p.y, 0.000001)
        assertTrue(painted.opacityGroups.isEmpty())
    }

    @Test
    fun styleCoversAllEarlierContoursAndDrawsBehindEarlierStyles() {
        val child = Shape.Group(listOf(ellipse), Transform(position = Track.Fixed(Vec2(12.0, 0.0))))
        val items = listOf(ellipse, fill, child, Shape.Fill(Track.Fixed(blue), rule = FillRule.EvenOdd))
        val paths = composition(layer(items)).evaluate(10.0)
        assertEquals(listOf(blue, red), paths.map { it.paint.color })
        assertEquals(listOf(2, 1), paths.map { it.contours.size })
        assertEquals(Vec2(12.0, -5.0), paths.first().contours[1].vertices.first())
        assertEquals(FillRule.EvenOdd, (paths.first().paint as Paint.Fill).rule)
    }

    @Test
    fun layerAndGroupOpacityEachCompositeTheirOwnOverlaps() {
        val child = Shape.Group(listOf(ellipse, fill, Shape.Fill(Track.Fixed(blue))),
            Transform(opacity = Track.Fixed(25.0)))
        val top = layer(listOf(child), Transform(opacity = Track.Fixed(50.0)))
        val background = layer(listOf(ellipse, fill))
        val paths = composition(top, background).evaluate(25.0)
        assertEquals(listOf(red, blue, red), paths.map { it.paint.color })
        assertEquals(emptyList(), paths[0].opacityGroups)
        assertEquals(listOf(0.5, 0.25), paths[1].opacityGroups.map { it.opacity })
        assertEquals(paths[1].opacityGroups, paths[2].opacityGroups)
        assertEquals(1.0, paths[2].opacity)
    }

    @Test
    fun keyframesHavePerChannelEasingAndCanHoldUntilNextKey() {
        val track = Track.Animated(listOf(
            Key(10.0, origin, easing = listOf(CubicEase(0.0, 0.0, 1.0, 1.0), CubicEase(0.0, 0.0, 1.0, 0.0))),
            Key(20.0, Vec2(80.0, 80.0), hold = true),
            Key(30.0, Vec2(20.0, 20.0)),
        ))
        val icon = composition(layer(listOf(ellipse, fill), Transform(position = track)))
        fun center(frame: Double) = icon.evaluate(frame).single().transform.map(origin)
        assertEquals(40.0, center(15.0).x)
        assertEquals(10.0, center(15.0).y, 0.0001)
        assertEquals(Vec2(80.0, 80.0), center(29.99))
        assertEquals(Vec2(20.0, 20.0), center(30.0))
    }

    @Test
    fun bezierMorphInterpolatesRelativeHandlesTogetherWithVertices() {
        val start = BezierPath(listOf(origin, Vec2(10.0, 10.0)), listOf(origin, Vec2(0.0, -4.0)),
            listOf(Vec2(4.0, 0.0), origin), false)
        val end = start.copy(vertices = listOf(origin, Vec2(20.0, 10.0)),
            outTangents = listOf(Vec2(8.0, 4.0), origin))
        val path = Shape.Path(Track.Animated(listOf(Key(10.0, start), Key(30.0, end))))
        val stroke = Shape.Stroke(Track.Fixed(blue), Track.Fixed(80.0), Track.Fixed(3.0), LineCap.Round)
        val draw = composition(layer(listOf(path, stroke))).evaluate(20.0).single()
        assertEquals(0.8, draw.opacity)
        assertEquals(Vec2(15.0, 10.0), draw.contours.single().vertices[1])
        assertEquals(Vec2(6.0, 2.0), draw.contours.single().outTangents[0])
        assertEquals(false, draw.contours.single().closed)
        assertEquals(LineCap.Round, (draw.paint as Paint.Stroke).cap)
    }
}
