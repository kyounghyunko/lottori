package com.ycgc.lottori.sample

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSHomeDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
fun MainViewController() = ComposeUIViewController {
    SampleApp { name ->
        val external = "${NSHomeDirectory()}/Documents/$name"
        val bytes = NSData.dataWithContentsOfFile(external)
            ?: return@SampleApp null
        ByteArray(bytes.length.toInt()).also { target ->
            if (target.isNotEmpty()) target.usePinned { memcpy(it.addressOf(0), bytes.bytes, bytes.length) }
        }
    }
}
