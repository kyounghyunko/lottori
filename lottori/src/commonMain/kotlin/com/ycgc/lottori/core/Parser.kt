package com.ycgc.lottori.core

import kotlin.text.CharacterCodingException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

enum class ParseMode { Strict, Preview }

enum class Severity { Warning, Error }

data class Diagnostic(val severity: Severity, val code: String, val path: String, val message: String, val layerName: String? = null)

data class ParseResult(val composition: LottoriComposition?, val diagnostics: List<Diagnostic>)

fun decodeJson(bytes: ByteArray, mode: ParseMode = ParseMode.Strict): ParseResult {
    if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
        val signature = (bytes[2].toInt() shl 8) or bytes[3].toInt()
        if (signature in setOf(0x0304, 0x0506, 0x0708)) {
            return failure("UnsupportedContainer", "Expected raw Lottie JSON; ZIP containers are unsupported.")
        }
    }
    val json = try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        return failure("invalid_utf8", "Expected UTF-8 encoded JSON.")
    }
    return parseLottori(json.removePrefix("\uFEFF"), mode)
}

private fun failure(code: String, message: String) =
    ParseResult(null, listOf(Diagnostic(Severity.Error, code, "$", message)))

internal fun parseLottori(json: String, mode: ParseMode = ParseMode.Strict): ParseResult {
    invalidTokenOffset(json)?.let { offset ->
        return ParseResult(null, listOf(Diagnostic(Severity.Error, "invalid_json", "$", "Invalid JSON token at offset $offset.")))
    }
    val root = try {
        Json.parseToJsonElement(json)
    } catch (error: SerializationException) {
        return ParseResult(null, listOf(Diagnostic(Severity.Error, "invalid_json", "$", error.message ?: "Invalid JSON.")))
    }
    val reader = LottieReader(mode)
    val document = try {
        reader.composition(Field(root, "$"))
    } catch (error: InvalidField) {
        reader.diagnostics += Diagnostic(Severity.Error, error.code, error.field.path, error.message.orEmpty(), error.field.layerName)
        null
    }
    return ParseResult(document.takeUnless { reader.diagnostics.any { it.severity == Severity.Error } }, reader.diagnostics.toList())
}

private val jsonNumber = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""")

// The JSON tree reader preserves arbitrary unquoted literals and accepts raw controls in strings.
private fun invalidTokenOffset(text: String): Int? {
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            ' ', '\t', '\n', '\r', '{', '}', '[', ']', ',', ':' -> index++
            '"' -> {
                index++
                while (index < text.length) {
                    val char = text[index++]
                    if (char < ' ') return index - 1
                    if (char == '"') break
                    if (char == '\\' && index < text.length) index++
                }
            }
            else -> {
                val start = index++
                while (index < text.length && text[index] !in " \t\n\r{}[],:\"") index++
                val token = text.substring(start, index)
                if (token != "null" && token != "true" && token != "false" && !jsonNumber.matches(token)) return start
            }
        }
    }
    return null
}

internal class InvalidField(val field: Field, message: String, val code: String = "invalid_value") : Exception(message)

internal data class Field(val value: JsonElement, val path: String, val layerName: String? = null) {
    fun invalid(message: String): Nothing = throw InvalidField(this, message)
    fun obj(): JsonObject = value as? JsonObject ?: invalid("Expected an object.")
    fun get(name: String): Field? = obj()[name]?.let { Field(it, "$path.$name", layerName) }
    fun require(name: String): Field = get(name) ?: throw InvalidField(copy(path = "$path.$name"), "Missing required field.")
    fun items(): List<Field> = (value as? JsonArray ?: invalid("Expected an array.")).mapIndexed { i, el -> Field(el, "$path[$i]", layerName) }
    fun number(): Double {
        val primitive = value as? JsonPrimitive
        return primitive?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
            ?: invalid("Expected a finite number.")
    }
    fun integer(): Int {
        val n = number()
        return n.toInt().takeIf { it.toDouble() == n } ?: invalid("Expected an integer.")
    }
    fun string(): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("Expected a string.")
    fun bool(): Boolean = (value as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: invalid("Expected a boolean.")
    fun flag(): Boolean = when (integer()) {
        0 -> false
        1 -> true
        else -> invalid("Expected 0 or 1.")
    }
}

internal class LottieReader(val mode: ParseMode) {
    val diagnostics = mutableListOf<Diagnostic>()

    fun unsupported(field: Field, code: String, message: String) {
        diagnostics += Diagnostic(if (mode == ParseMode.Strict) Severity.Error else Severity.Warning, code, field.path, message, field.layerName)
    }

    fun composition(root: Field): LottoriComposition {
        fun dimension(name: String): Int = root.require(name).run {
            integer().takeIf { it > 0 } ?: invalid("Expected a positive dimension.")
        }
        val width = dimension("w")
        val height = dimension("h")
        val fps = root.require("fr").run { number().takeIf { it > 0 } ?: invalid("Expected a positive frame rate.") }
        val ip = root.require("ip").number()
        val op = root.require("op").run { number().takeIf { it > ip } ?: invalid("Expected out point after in point.") }
        val seconds = (op - ip) / fps
        if (!seconds.isFinite() || seconds <= 0.0) root.require("op").invalid("Duration must be finite and positive.")
        root.get("ver")?.let { version ->
            val target = version.integer()
            if (target !in 10000..10001) {
                diagnostics += Diagnostic(Severity.Warning, "UnknownSpecVersion", version.path, "Unrecognized target specification $target.")
            }
        }
        root.get("assets")?.items()?.forEach { unsupported(it, "Asset", "External and precomposition assets are unsupported.") }
        root.get("slots")?.takeIf { it.obj().isNotEmpty() }?.let {
            throw InvalidField(it, "Cannot determine which layers use these slot overrides.", "Slots")
        }

        val nodes = root.require("layers").items().map { it.copy(layerName = it.get("nm")?.string()) }
        val ids = mutableMapOf<Int, Int>()
        nodes.forEachIndexed { index, node ->
            node.get("ind")?.let { id ->
                if (ids.put(id.integer(), index) != null) id.invalid("Duplicate layer index.")
            }
        }
        val omitted = BooleanArray(nodes.size)
        val parents = nodes.map { node ->
            node.get("parent")?.let { ids[it.integer()] ?: it.invalid("Unknown parent layer.") }
        }
        val parentFirst = orderByParent(nodes, parents)
        for ((index, node) in nodes.withIndex()) {
            val matte = node.get("tt") ?: continue
            if (matte.integer() == 0) continue
            val source = node.get("tp")?.let { ids[it.integer()] ?: it.invalid("Missing matte source.") } ?: (index - 1)
            if (source !in nodes.indices || source == index) matte.invalid("Cannot resolve a distinct matte source.")
            omitted[source] = true
            omitted[index] = true
        }

        val layers = nodes.mapIndexed { index, node ->
            val before = diagnostics.size
            val layer = try {
                layer(node, ip, op)
            } catch (_: UnsupportedProperty) {
                null
            }
            if (diagnostics.size > before) omitted[index] = true
            layer
        }
        for (index in parentFirst) {
            val parent = parents[index] ?: continue
            if (omitted[parent] && !omitted[index]) {
                omitted[index] = true
                unsupported(nodes[index].require("parent"), "OmittedParent", "Parent layer is unavailable.")
            }
        }
        return LottoriComposition(width, height, fps, ip, op, layers.filterIndexed { i, _ -> !omitted[i] }.filterNotNull())
    }

    private fun orderByParent(nodes: List<Field>, parents: List<Int?>): List<Int> {
        val ordered = mutableSetOf<Int>()
        for (index in nodes.indices) {
            var parent: Int? = index
            val chain = mutableSetOf<Int>()
            while (parent != null && parent !in ordered) {
                if (!chain.add(parent)) nodes[parent].require("parent").invalid("Layer parenting contains a cycle.")
                parent = parents[parent]
            }
            ordered += chain.toList().asReversed()
        }
        return ordered.toList()
    }

    private fun layer(node: Field, ip: Double, op: Double): Layer {
        val type = node.require("ty")
        val typeId = type.integer()
        if (typeId !in 3..4) {
            val code = when (typeId) {
                0 -> "Precomposition"
                1 -> "SolidLayer"
                2 -> "ImageLayer"
                5 -> "TextLayer"
                else -> "UnknownLayer"
            }
            unsupported(type, code, "Unsupported layer type $typeId.")
        }
        node.get("masksProperties")?.takeIf { it.items().isNotEmpty() }?.let { unsupported(it, "Mask", "Layer uses masks.") }
        node.get("tt")?.takeIf { it.integer() != 0 }?.let { unsupported(it, "Matte", "Layer uses a track matte.") }
        node.get("td")?.takeIf { it.integer() != 0 }?.let { unsupported(it, "MatteSource", "Layer supplies a track matte.") }
        node.get("ddd")?.takeIf { it.flag() }?.let { unsupported(it, "Layer3D", "Layer uses 3D transforms.") }
        node.get("bm")?.takeIf { it.integer() != 0 }?.let { unsupported(it, "BlendMode", "Layer uses a blend mode.") }
        node.get("ef")?.takeIf { it.items().isNotEmpty() }?.let { unsupported(it, "Effects", "Layer uses effects.") }
        node.get("ao")?.takeIf { it.flag() }?.let { unsupported(it, "AutoOrient", "Layer uses auto-orient.") }
        node.get("sr")?.takeIf { it.number() != 1.0 }?.let { unsupported(it, "TimeStretch", "Layer uses a time stretch.") }
        node.get("st")?.takeIf { it.number() != 0.0 }?.let { unsupported(it, "StartTime", "Layer uses a nonzero start time.") }
        node.get("tm")?.let { unsupported(it, "TimeRemap", "Layer uses time remapping.") }
        val transform = node.get("ks")?.let { transform(it) } ?: Transform()
        val shapes = if (typeId == 4) {
            val group = shapeGroup(node.require("shapes"), 0)
            if (node.get("hd")?.bool() == true) emptyList()
            else if (group.transform == Transform()) group.items else listOf(group)
        } else emptyList()
        return Layer(node.get("ind")?.integer(), node.get("parent")?.integer(), node.get("ip")?.number() ?: ip,
            node.get("op")?.number() ?: op, transform, shapes)
    }
}

internal class UnsupportedProperty : Exception()
