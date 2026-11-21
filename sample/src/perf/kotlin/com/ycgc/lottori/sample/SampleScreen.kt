package com.ycgc.lottori.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

internal data class PerfRun(val icons: Int, val id: String, val engine: String)
@Composable internal expect fun currentRun(): PerfRun
internal expect fun allocatedBytes(): Long?
internal expect fun publishResult(line: String)

private class Samples(val count: Int) {
    val clock = TimeSource.Monotonic.markNow()
    fun now() = clock.elapsedNow().inWholeNanoseconds
    var received = 0L
    var decoded = 0L
    var firstDraw = 0L
    var draws = 0
    var observed = 0
    var sampling = false
    val times = LongArray(180)
    val paint = LongArray(180)
    val iconDraws = IntArray(count)
    private var totalPaint = 0L

    fun endIcon(index: Int, start: Long, end: Long) {
        if (sampling) iconDraws[index]++
        if (index == 0) totalPaint = 0
        totalPaint += end - start
        if (index + 1 != count) return
        if (draws++ == 0) firstDraw = end - received
        if (sampling && observed < times.size) {
            times[observed] = end
            paint[observed++] = totalPaint
        }
    }
}

@Composable
fun SampleScreen(readBytes: (String) -> ByteArray) {
    val run = currentRun()
    require(run.icons in listOf(0, 1, 10))
    val samples = remember(run) { Samples(maxOf(1, run.icons)) }
    var composition by remember(run) { mutableStateOf<PerfAnimation?>(null) }
    LaunchedEffect(run) {
        composition = withContext(Dispatchers.Default) {
            val bytes = readBytes("input.json")
            samples.received = samples.now()
            val parsed = decodeAnimation(run.engine, bytes)
            samples.decoded = samples.now() - samples.received
            parsed
        }
    }
    val input = composition ?: return
    var progress by remember(run) { mutableFloatStateOf(0f) }
    Column {
        repeat((samples.count + 4) / 5) { row ->
            Row {
                repeat(minOf(5, samples.count - row * 5)) { col ->
                    val index = row * 5 + col
                    input.Draw({ progress }, Modifier.size(48.dp).drawWithContent {
                        val start = samples.now()
                        drawContent()
                        samples.endIcon(index, start, samples.now())
                    })
                }
            }
        }
    }
    LaunchedEffect(input, run) {
        val origin = withFrameNanos { it }
        val callbacks = LongArray(121)
        var initialAllocated: Long? = null
        repeat(145) { index ->
            val now = withFrameNanos { it }
            if (run.icons > 0) progress = ((now - origin) % 1_000_000_000L) / 1_000_000_000f
            if (index == 24) {
                samples.sampling = true
                initialAllocated = allocatedBytes()
            }
            if (index >= 24) callbacks[index - 24] = now
        }
        samples.sampling = false
        val allocated = allocatedBytes()?.let { end -> initialAllocated?.let { end - it } }
        fun data(values: LongArray, size: Int) = values.take(size).joinToString(",")
        val interval = (1 until samples.observed).map { samples.times[it] - samples.times[it - 1] }
        if (run.icons > 0) progress = 0.5f
        publishResult("""{"id":"${run.id}","engine":"${run.engine}","icons":${run.icons},"decodeNs":${samples.decoded},"firstDrawNs":${samples.firstDraw},"draws":${samples.draws},"frames":${samples.observed},"iconDraws":[${samples.iconDraws.joinToString(",")}],"allocatedBytes":$allocated,"clockNs":[${callbacks.asList().zipWithNext { a,b -> b-a }.joinToString(",")}],"intervalNs":[${interval.joinToString(",")}],"paintNs":[${data(samples.paint,samples.observed)}]}""")
    }
}
