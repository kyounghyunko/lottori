package com.ycgc.lottori

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalComposeUiApi::class)
class LoadingTest {
    @Test
    fun resourceReloadsWhenItsFullPathOrExplicitKeyChanges() {
        val home = "files/home/icon.json"
        val profile = "files/profile/icon.json"
        val path = mutableStateOf(home)
        val revision = mutableStateOf<Int?>(null)
        val requests = CopyOnWriteArrayList<String>()
        val readBytes: suspend (String) -> ByteArray = { requested ->
            requests.add(requested)
            """{"w":${requests.size},"h":1,"ip":0,"op":1,"fr":1,"layers":[]}""".encodeToByteArray()
        }
        var observed: LottoriLoadState = LottoriLoadState.Loading
        val scene = ImageComposeScene(100, 40, Density(1f)) {
            val state = rememberLottoriComposition(path.value, readBytes, key = revision.value)
            SideEffect { observed = state }
        }
        fun awaitWidth(width: Int) = scene.until { (observed as? LottoriLoadState.Ready)?.composition?.width == width }
        try {
            awaitWidth(1)
            path.value = profile
            awaitWidth(2)
            revision.value = 1
            awaitWidth(3)
            path.value = home
            awaitWidth(4)
            assertEquals(listOf(home, profile, profile, home), requests.toList())
        } finally {
            scene.close()
        }
    }

    @Test
    fun replacingTheKeyCancelsTheOldRequestAndKeepsTheNewResult() {
        val key = mutableStateOf("old")
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val oldCancelled = CompletableDeferred<Unit>()
        val oldReturned = CompletableDeferred<Unit>()
        val newInput = CompletableDeferred<ByteArray>()
        var observed: LottoriLoadState = LottoriLoadState.Loading
        val scene = ImageComposeScene(100, 40, Density(1f)) {
            val current = key.value
            val state = rememberLottoriComposition(current) {
                if (current == "old") {
                    oldStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (_: CancellationException) {
                        oldCancelled.complete(Unit)
                        withContext(NonCancellable) { releaseOld.await() }
                        oldReturned.complete(Unit)
                        byteArrayOf(0)
                    }
                } else newInput.await()
            }
            SideEffect { observed = state }
        }
        try {
            scene.until { oldStarted.isCompleted }
            key.value = "new"
            scene.until { oldCancelled.isCompleted }
            assertEquals(LottoriLoadState.Loading, observed)
            newInput.complete(GoldenAssets.json.encodeToByteArray())
            scene.until { observed is LottoriLoadState.Ready }
            releaseOld.complete(Unit)
            scene.until { oldReturned.isCompleted }
            scene.render().close()
            assertEquals(100, assertIs<LottoriLoadState.Ready>(observed).composition.width)
        } finally {
            releaseOld.complete(Unit)
            scene.close()
        }
    }

    @Test
    fun aNewRequestImmediatelyReplacesReadyAndFailedSuppliersCanRecover() {
        val key = mutableStateOf("missing")
        val input = CompletableDeferred<ByteArray>()
        var observed: LottoriLoadState = LottoriLoadState.Loading
        val scene = ImageComposeScene(100, 40, Density(1f)) {
            val requested = key.value
            val state = rememberLottoriComposition(requested) {
                when (requested) {
                    "missing" -> error("Test resource missing")
                    "circle" -> GoldenAssets.json.encodeToByteArray()
                    else -> input.await()
                }
            }
            SideEffect { observed = state }
        }
        try {
            scene.until { observed is LottoriLoadState.Failed }
            assertEquals("LoadFailed", assertIs<LottoriLoadState.Failed>(observed).diagnostics.single().code)
            key.value = "circle"
            scene.until { observed is LottoriLoadState.Ready }
            key.value = "unsupported"
            scene.render().close()
            assertEquals(LottoriLoadState.Loading, observed)
            input.complete("""{"w":1,"h":1,"ip":0,"op":1,"fr":1,"layers":[{"ty":5}]}""".encodeToByteArray())
            scene.until { observed is LottoriLoadState.Failed }
            assertTrue(assertIs<LottoriLoadState.Failed>(observed).diagnostics.isNotEmpty())
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.until(predicate: () -> Boolean) {
        val start = TimeSource.Monotonic.markNow()
        while (!predicate()) {
            render().close()
            check(start.elapsedNow() < 10.seconds) { "Timed out while waiting for the Compose load state" }
            Thread.sleep(1)
        }
    }
}
