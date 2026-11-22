package com.ycgc.lottori

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ycgc.lottori.core.Diagnostic
import com.ycgc.lottori.core.LottoriComposition
import com.ycgc.lottori.core.ParseMode
import com.ycgc.lottori.core.Severity
import com.ycgc.lottori.core.decodeJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface LottoriLoadState {
    data object Loading : LottoriLoadState
    data class Ready(val composition: LottoriComposition, val diagnostics: List<Diagnostic>) : LottoriLoadState
    data class Failed(val diagnostics: List<Diagnostic>) : LottoriLoadState
}

/** Reads [path] with the app's resource reader. A change to [path], [key], or [mode] reloads the file. */
@Composable
fun rememberLottoriComposition(
    path: String,
    readBytes: suspend (String) -> ByteArray,
    key: Any? = null,
    mode: ParseMode = ParseMode.Strict,
): LottoriLoadState {
    val resourceKey = if (key == null) path else path to key
    return rememberLottoriComposition(resourceKey, mode) { readBytes(path) }
}

/** Reloads when [key] or [mode] changes. Include the resource version in [key] when its contents change. */
@Composable
fun rememberLottoriComposition(
    key: Any?,
    mode: ParseMode = ParseMode.Strict,
    loader: suspend () -> ByteArray,
): LottoriLoadState {
    var state by remember(key, mode) { mutableStateOf<LottoriLoadState>(LottoriLoadState.Loading) }
    LaunchedEffect(key, mode) {
        state = withContext(Dispatchers.Default) {
            val bytes = try {
                loader()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return@withContext LottoriLoadState.Failed(listOf(
                    Diagnostic(Severity.Error, "LoadFailed", "$", error.message ?: "Could not load animation bytes."),
                ))
            }
            ensureActive()
            val result = decodeJson(bytes, mode)
            val composition = result.composition
            if (composition == null) LottoriLoadState.Failed(result.diagnostics)
            else LottoriLoadState.Ready(composition, result.diagnostics)
        }
    }
    return state
}
