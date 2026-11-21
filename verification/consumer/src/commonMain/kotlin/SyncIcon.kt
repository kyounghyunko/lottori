import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ycgc.lottori.LottoriAnimation
import com.ycgc.lottori.LottoriLoadState
import com.ycgc.lottori.rememberLottoriComposition

@Composable
fun SyncIcon(
    key: String,
    readAsset: suspend () -> ByteArray,
    progress: () -> Float,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val state = rememberLottoriComposition(key, loader = readAsset)) {
        LottoriLoadState.Loading -> Unit
        is LottoriLoadState.Failed -> fallback()
        is LottoriLoadState.Ready -> LottoriAnimation(
            composition = state.composition,
            progress = progress,
            modifier = modifier,
            contentDescription = "동기화",
        )
    }
}
