package com.ycgc.lottori.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTest {
    private fun document(vararg layers: String) =
        """{"w":100,"h":40,"fr":30,"ip":10,"op":40,"layers":[${layers.joinToString(",")}]}"""

    private val circle = """{"ty":"el","p":{"k":[0,0]},"s":{"k":[10,10]}}"""
    private val fill = """{"ty":"fl","c":{"k":[1,0,0,1]},"o":{"k":100}}"""
    private fun shapeLayer(shapes: String, extra: String = "") =
        """{"ty":4,"ind":17,${extra}"shapes":[$shapes]}"""

    @Test
    fun strictRejectsUnsupportedFeaturesAndPreviewDropsWholeAffectedLayers() {
        val input = document(
            """{"ty":5,"nm":"caption"}""",
            shapeLayer("""$circle,{"ty":"gr","it":[{"ty":"tm"}]},$fill""", """"nm":"checkmark","""),
            """{"ty":4,"shapes":[$circle,$fill]}""",
        ).encodeToByteArray()
        val strict = decodeJson(input)
        assertNull(strict.composition)
        val expectedPaths = listOf("$.layers[0].ty", "$.layers[1].shapes[1].it[0]")
        assertEquals(expectedPaths, strict.diagnostics.map { it.path })
        assertEquals(listOf("TextLayer", "TrimPath"), strict.diagnostics.map { it.code })
        assertEquals(listOf("caption", "checkmark"), strict.diagnostics.map { it.layerName })
        assertTrue(strict.diagnostics.all { it.severity == Severity.Error })

        val preview = decodeJson(input, ParseMode.Preview)
        assertEquals(1, assertNotNull(preview.composition).evaluate(10.0).size)
        assertEquals(expectedPaths, preview.diagnostics.map { it.path })
        assertTrue(preview.diagnostics.all { it.severity == Severity.Warning })
    }

    @Test
    fun byteInputDistinguishesZipSignaturesUtf8AndJsonSyntax() {
        val raw = document().encodeToByteArray()
        assertNotNull(decodeJson(raw).composition)
        assertNotNull(decodeJson(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + raw).composition)
        for (signature in listOf(byteArrayOf(3,4), byteArrayOf(5,6), byteArrayOf(7,8))) {
            val zip = byteArrayOf(0x50, 0x4b) + signature
            for (mode in ParseMode.entries) {
                val result = decodeJson(zip, mode)
                assertNull(result.composition)
                assertEquals("UnsupportedContainer", result.diagnostics.single().code)
            }
        }
        assertEquals("invalid_utf8", decodeJson(byteArrayOf(0xc3.toByte())).diagnostics.single().code)
        assertEquals("invalid_json", decodeJson("{".encodeToByteArray()).diagnostics.single().code)
    }

    @Test
    fun oldExporterVersionsAndUnknownTargetVersionsUseActualNodes() {
        fun version(field: String) = document().replace("{", "{$field,", ignoreCase = false).encodeToByteArray()
        for (field in listOf(""""v":"5.12.2"""", """"ver":10001""")) {
            val result = decodeJson(version(field))
            assertTrue(result.diagnostics.isEmpty())
            assertNotNull(result.composition)
        }
        val unknown = decodeJson(version(""""ver":90007"""))
        assertNotNull(unknown.composition)
        assertEquals(Severity.Warning, unknown.diagnostics.single().severity)
        assertEquals("$.ver", unknown.diagnostics.single().path)
        assertEquals("UnknownSpecVersion", unknown.diagnostics.single().code)
    }

    @Test
    fun malformedPropertyIdentifiesItsArrayElement() {
        val input = document(shapeLayer(circle.replace("[10,10]", """[10,"ten"]""") + "," + fill))
        val result = parseLottori(input)
        assertNull(result.composition)
        assertEquals("$.layers[0].shapes[0].s.k[1]", result.diagnostics.single().path)
        assertEquals(Severity.Error, result.diagnostics.single().severity)
    }

    @Test
    fun unrelatedTypesAndMalformedJsonReturnDiagnostics() {
        for (text in listOf("{", "null", "[]", """{"w":false}""")) {
            val result = parseLottori(text)
            assertNull(result.composition)
            assertEquals(Severity.Error, result.diagnostics.single().severity)
        }
    }

    @Test
    fun invalidTokensInUnusedFieldsAreStillInvalidJson() {
        for (literal in listOf("01", ".5", "undefined", "\"line\nbreak\"")) {
            val input = document().replace("\"layers\":", "\"extra\":$literal,\"layers\":")
            val result = parseLottori(input)
            assertNull(result.composition)
            assertEquals("invalid_json", result.diagnostics.single().code)
        }
    }

    @Test
    fun malformedParentGraphsFailAtTheBrokenReference() {
        val missing = parseLottori(document("""{"ty":3,"ind":1,"parent":2}"""))
        assertNull(missing.composition)
        assertEquals("$.layers[0].parent", missing.diagnostics.single().path)
        val cycle = parseLottori(document("""{"ty":3,"ind":3,"parent":4}""", """{"ty":3,"ind":4,"parent":3}"""))
        assertNull(cycle.composition)
        assertTrue(cycle.diagnostics.single().path.endsWith(".parent"))
    }

    @Test
    fun matteSkipsBothSourceAndTarget() {
        val input = document(shapeLayer("$circle,$fill"), """{"ty":4,"tt":1,"shapes":[$circle,$fill]}""")
        val result = parseLottori(input, ParseMode.Preview)
        assertTrue(assertNotNull(result.composition).evaluate(10.0).isEmpty())
        assertEquals("$.layers[1].tt", result.diagnostics.single().path)
    }

    @Test
    fun aHiddenParentStillMovesTheChild() {
        val hidden = """{"ty":3,"hd":true,"ind":37,"ks":{"p":{"k":[12,3]}}}"""
        val child = shapeLayer("$circle,$fill", """"parent":37,""")
        val result = parseLottori(document(child, hidden), ParseMode.Strict)
        val rendered = assertNotNull(result.composition).evaluate(10.0).single()
        assertEquals(Vec2(12.0, 3.0), rendered.transform.map(Vec2(0.0, 0.0)))
    }

    @Test
    fun aLegacyTerminalKeyGetsItsValueFromThePreviousEndpoint() {
        val movement = """{"a":1,"k":[{"t":10,"s":[5,3],"e":[15,9]},{"t":20}]}"""
        val child = shapeLayer("$circle,$fill", """"ks":{"p":$movement},""")
        val result = parseLottori(document(child), ParseMode.Strict)
        val composition = assertNotNull(result.composition)
        assertEquals(Vec2(10.0, 6.0), composition.evaluate(15.0).single().transform.map(Vec2(0.0, 0.0)))
        assertEquals(Vec2(15.0, 9.0), composition.evaluate(20.0).single().transform.map(Vec2(0.0, 0.0)))
        val explicitEnd = document(child).replace("""{"t":20}""", """{"t":20,"s":[25,9]}""")
        val explicit = assertNotNull(parseLottori(explicitEnd, ParseMode.Strict).composition)
        assertEquals(Vec2(15.0, 6.0), explicit.evaluate(15.0).single().transform.map(Vec2(0.0, 0.0)))
    }

    @Test
    fun pathTangentsAndTemporalHandlesCannotBeConfused() {
        val line = """{"ty":"sh","ks":{"k":{"v":[[1,2],[3,4]],"i":[[0,0]],"o":[[0,0],[0,0]],"c":false}}}"""
        val result = parseLottori(document(shapeLayer("$line,$fill")))
        assertNull(result.composition)
        assertEquals("$.layers[0].shapes[0].ks.k.i", result.diagnostics.single().path)
    }

    @Test
    fun expressionsDropTheLayerAndIdentifyTheProperty() {
        val expression = circle.replace("""{"k":[0,0]}""", """{"x":"time * 10"}""")
        val result = parseLottori(document(shapeLayer("$expression,$fill")), ParseMode.Preview)
        assertEquals("$.layers[0].shapes[0].p.x", result.diagnostics.single().path)
        assertTrue(assertNotNull(result.composition).evaluate(10.0).isEmpty())
    }

    @Test
    fun unsupportedMasksAndParentsCannotExposeTheirChildren() {
        val root = """{"ty":3,"ind":99,"nm":"root","ks":{"p":{"s":true}}}"""
        val parent = """{"ty":3,"ind":27,"parent":99}"""
        val child = shapeLayer("$circle,$fill", """"parent":27,""")
        val masked = """{"ty":4,"ind":24,"masksProperties":[{}],"shapes":[$circle,$fill]}"""
        val owner = """{"ty":4,"ind":25,"shapes":[$circle,$fill]}"""
        val result = parseLottori(document(child, owner, parent, root, masked), ParseMode.Preview)
        assertEquals(1, assertNotNull(result.composition).evaluate(10.0).size)
        assertEquals(setOf("SplitPosition","Mask","OmittedParent"), result.diagnostics.map { it.code }.toSet())
    }

    @Test
    fun unresolvedMatteScopesFailEvenInPreview() {
        val missingPrevious = document("""{"ty":3,"ind":1,"tt":1}""")
        val missingExplicit = document("""{"ty":3,"tt":1,"tp":25}""")
        val selfReference = document("""{"ty":3,"ind":25,"tt":1,"tp":25}""")
        for (input in listOf(missingPrevious, missingExplicit, selfReference)) {
            val result = parseLottori(input, ParseMode.Preview)
            assertNull(result.composition)
            assertEquals(Severity.Error, result.diagnostics.single().severity)
        }
    }

    @Test
    fun nonDefaultTimingAnimatedColorsAndMorphsAreOutsideVersionOne() {
        val colors = """{"ty":"fl","c":{"a":1,"k":[{"t":10,"s":[1,0,0]},{"t":40,"s":[0,1,0]}]}}"""
        val morph = """{"ty":"sh","ks":{"a":1,"k":[]}}"""
        val examples = mapOf(
            "StartTime" to shapeLayer("$circle,$fill", """"st":10,"""),
            "TimeStretch" to shapeLayer("$circle,$fill", """"sr":2,"""),
            "TimeRemap" to shapeLayer("$circle,$fill", """"tm":{"k":1},"""),
            "AnimatedColor" to shapeLayer("$circle,$colors"),
            "PathMorph" to shapeLayer("$morph,$fill"),
        )
        for ((code, layer) in examples) {
            val strict = parseLottori(document(layer))
            assertNull(strict.composition)
            assertEquals(code, strict.diagnostics.single().code)
            val preview = parseLottori(document(layer), ParseMode.Preview)
            assertTrue(assertNotNull(preview.composition).evaluate(10.0).isEmpty())
        }
    }
}
