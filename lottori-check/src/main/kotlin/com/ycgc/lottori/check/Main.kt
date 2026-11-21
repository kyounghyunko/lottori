package com.ycgc.lottori.check

import com.ycgc.lottori.core.ParseMode
import com.ycgc.lottori.core.decodeJson
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val mode = if (args.firstOrNull() == "--preview") ParseMode.Preview else ParseMode.Strict
    val files = if (args.firstOrNull() in listOf("--preview", "--strict")) args.drop(1) else args.toList()
    if (files.isEmpty() || files.any { it.startsWith("--") }) {
        System.err.println("Usage: lottori-check [--strict|--preview] FILE ...")
        exitProcess(2)
    }

    var failed = false
    for (name in files) {
        val bytes = try {
            File(name).readBytes()
        } catch (error: IOException) {
            System.err.println("$name: ${error.message}")
            failed = true
            continue
        }
        val result = decodeJson(bytes, mode)
        for (issue in result.diagnostics) {
            val layer = issue.layerName?.let { " layer=$it" }.orEmpty()
            println("$name ${issue.path} [${issue.severity} ${issue.code}]$layer ${issue.message}")
        }
        val composition = result.composition
        if (composition == null) failed = true
        else println("$name: ${composition.width} x ${composition.height}, ${composition.durationSeconds}s, ${result.diagnostics.size} diagnostic(s)")
    }
    if (failed) exitProcess(1)
}
