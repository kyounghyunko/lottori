package com.ycgc.lottori.sample

import android.app.Activity
import android.content.ContextWrapper
import android.os.Debug
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable internal actual fun currentRun(): PerfRun {
    val context = LocalContext.current
    return remember(context) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().first()
        PerfRun(activity.intent.getIntExtra("icons", 1), activity.intent.getStringExtra("run") ?: "manual",
            activity.intent.getStringExtra("engine") ?: "lottori")
    }
}
internal actual fun allocatedBytes(): Long? = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
internal actual fun publishResult(line: String) {
    line.chunked(2500).forEachIndexed { index, text -> Log.i("LottoriPerf", "PART $index $text") }
    Log.i("LottoriPerf", "DONE")
}
