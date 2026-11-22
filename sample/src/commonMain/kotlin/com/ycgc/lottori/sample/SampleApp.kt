package com.ycgc.lottori.sample

import androidx.compose.runtime.Composable
import com.ycgc.lottori.sample.resources.Res

@Composable
fun SampleApp(readLocalFile: (String) -> ByteArray?) {
    SampleScreen { path -> readLocalFile(path.removePrefix("files/")) ?: Res.readBytes(path) }
}
