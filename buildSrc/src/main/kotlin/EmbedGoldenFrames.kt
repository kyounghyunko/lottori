import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.ByteBuffer
import java.util.Base64
import javax.imageio.ImageIO

abstract class EmbedGoldenFrames : DefaultTask() {
    @get:Input abstract val captureFrames: Property<Boolean>
    @get:InputDirectory abstract val fixtures: DirectoryProperty
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = fixtures.get().asFile
        val frames = root.resolve("frames")
        val images = frames.walkTopDown().filter { it.extension == "png" }.sortedBy { it.path }.toList()
        val encoder = Base64.getEncoder()
        val text = buildString {
            appendLine("package com.ycgc.lottori")
            appendLine("import kotlin.io.encoding.Base64")
            appendLine("internal object GoldenAssets {")
            appendLine("    const val capture = ${captureFrames.get()}")
            appendLine("    val inputs = mapOf(")
            val inputs = images.map { it.nameWithoutExtension.substringBeforeLast('-') } + "trim-path"
            for (name in inputs.distinct()) {
                val bytes = root.resolve("$name.json").readBytes()
                appendLine("        \"$name\" to Base64.decode(\"${encoder.encodeToString(bytes)}\"),")
            }
            appendLine("    )")
            appendLine("    val json get() = inputs.getValue(\"moving-circle\").decodeToString()")
            appendLine("    val frames = mapOf(")
            for (file in images) {
                val image = ImageIO.read(file)
                val pixels = ByteBuffer.allocate(image.width * image.height * 4)
                for (y in 0 until image.height) for (x in 0 until image.width) pixels.putInt(image.getRGB(x, y))
                val key = file.relativeTo(frames).invariantSeparatorsPath.removeSuffix(".png")
                appendLine("        \"$key\" to Frame(${image.width}, ${image.height}, Base64.decode(\"${encoder.encodeToString(pixels.array())}\")),")
            }
            appendLine("    )")
            appendLine("    class Frame(val width: Int, val height: Int, private val bytes: ByteArray) {")
            appendLine("        fun pixel(x: Int, y: Int): Int {")
            appendLine("            val offset = 4 * (y * width + x)")
            appendLine("            return (0..3).fold(0) { pixel, i -> (pixel shl 8) or (bytes[offset+i].toInt() and 255) }")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
        val file = output.file("GoldenAssets.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(text)
    }
}
