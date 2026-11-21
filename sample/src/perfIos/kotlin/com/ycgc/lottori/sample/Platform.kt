package com.ycgc.lottori.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.rawValue
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSProcessInfo
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.free
import platform.posix.malloc

@Composable internal actual fun currentRun(): PerfRun = remember {
    val env = NSProcessInfo.processInfo.environment
    PerfRun((env["LOTTORI_ICONS"] as? String)?.toInt() ?: 1, env["LOTTORI_RUN"] as? String ?: "manual",
        env["LOTTORI_ENGINE"] as? String ?: "lottori")
}
@OptIn(ExperimentalForeignApi::class)
private object MallocMarkers {
    val enabled = NSProcessInfo.processInfo.environment["MallocStackLoggingNoCompact"] == "1"
    val pointers = mutableListOf<COpaquePointer>()

    fun mark() {
        if (enabled) pointers += checkNotNull(malloc(if (pointers.isEmpty()) 29_999uL else 39_999uL))
    }

    fun result(line: String): String {
        if (!enabled) return line
        check(pointers.size == 2)
        val addresses = pointers.joinToString(",") { "\"0x${it.rawValue.toLong().toString(16)}\"" }
        pointers.forEach { free(it) }
        pointers.clear()
        return line.dropLast(1) + ",\"mallocMarkers\":[${addresses}]}"
    }
}

internal actual fun allocatedBytes(): Long? {
    MallocMarkers.mark()
    return null
}
@OptIn(ExperimentalForeignApi::class)
internal actual fun publishResult(line: String) {
    val fp = checkNotNull(fopen("${NSHomeDirectory()}/Documents/lottori-perf.json", "w"))
    try { check(fputs(MallocMarkers.result(line), fp) >= 0) } finally { fclose(fp) }
}
