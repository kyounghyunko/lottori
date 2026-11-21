package com.ycgc.lottori.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ycgc.lottori.LottoriAnimation
import com.ycgc.lottori.LottoriLoadState
import com.ycgc.lottori.rememberLottoriComposition
import com.ycgc.lottori.rememberLottoriPlayback

private enum class Input(val label: String, val file: String? = null) {
    PaperFlight("Paper flight", "paper-flight.json"),
    Moving("Moving circle", "moving-circle.json"), Static("Static circle", "static-circle.json"),
    Trim("Trim path", "trim-path.json"), Broken("Damaged JSON"), Archive("ZIP");

    fun read(loader: (String) -> ByteArray): ByteArray = when (this) {
        Broken -> "{".encodeToByteArray()
        Archive -> byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(18)
        else -> loader(requireNotNull(file))
    }
}

private sealed interface Position {
    data class Fixed(val percent: Int) : Position
    data class Loop(val playing: Boolean) : Position
}

private val ink = Color(0xff1d2d44)
private val muted = Color(0xff445873)
private val paper = Color(0xfff4f6fa)
private val accent = Color(0xff244b95)
private val corner = RoundedCornerShape(10.dp)

@Composable
fun SampleScreen(readBytes: (String) -> ByteArray) {
    var input by remember { mutableStateOf(Input.PaperFlight) }
    var position by remember(input) {
        mutableStateOf<Position>(if (input == Input.PaperFlight) Position.Loop(true) else Position.Fixed(0))
    }
    val result = rememberLottoriComposition(key = input) { input.read(readBytes) }
    Column(Modifier.fillMaxSize().background(paper).safeContentPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("Lottori", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ink))
        Caption("Animation")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (choice in Input.entries) Action(choice.label, choice == input) { input = choice }
        }
        Column(Modifier.fillMaxWidth().clip(corner).background(Color.White).border(1.dp, Color(0xffccd5e2), corner)
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Caption(input.label)
            val viewport = if (input == Input.PaperFlight) Modifier.aspectRatio(960f / 432f) else Modifier.height(122.dp)
            Box(Modifier.fillMaxWidth().then(viewport), contentAlignment = Alignment.Center) {
                when (result) {
                    LottoriLoadState.Loading -> Caption("Loading…")
                    is LottoriLoadState.Failed -> StaticFallback()
                    is LottoriLoadState.Ready -> {
                        val fixed = position as? Position.Fixed
                        val progress: () -> Float = if (fixed != null) {
                            { fixed.percent / 100f }
                        } else {
                            rememberLottoriPlayback(result.composition, Int.MAX_VALUE, (position as Position.Loop).playing)
                                .let { playback -> { playback.value } }
                        }
                        LottoriAnimation(result.composition, progress = progress, contentDescription = "Animation preview",
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
            if (result is LottoriLoadState.Failed) {
                Caption("Static fallback")
                for (issue in result.diagnostics) {
                    val label = listOfNotNull(issue.code, issue.path, issue.layerName).joinToString("  ")
                    BasicText(label, style = TextStyle(fontSize = 14.sp, color = muted))
                }
            }
        }
        if (result is LottoriLoadState.Ready) {
            Caption("Progress")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in listOf(0, 50, 100)) Action("$p%", position == Position.Fixed(p)) { position = Position.Fixed(p) }
                val playing = (position as? Position.Loop)?.playing == true
                Action(if (playing) "Pause" else "Play", playing) { position = Position.Loop(!playing) }
            }
        }
    }
}

@Composable
private fun StaticFallback() {
    Canvas(Modifier.size(40.dp).semantics { contentDescription = "Static status icon" }) {
        drawCircle(ink)
        drawLine(Color.White, Offset(size.width * 0.26f, size.height * 0.50f),
            Offset(size.width * 0.43f, size.height * 0.66f), size.width * 0.075f, StrokeCap.Round)
        drawLine(Color.White, Offset(size.width * 0.43f, size.height * 0.66f),
            Offset(size.width * 0.73f, size.height * 0.35f), size.width * 0.075f, StrokeCap.Round)
    }
}

@Composable
private fun Caption(text: String) =
    BasicText(text, style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium))

@Composable
private fun Action(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(corner).background(if (selected) accent else Color.White)
        .border(1.dp, if (selected) accent else Color(0xffb5c2d6), corner)
        .clickable(role = Role.Button, onClick = onClick)
        .defaultMinSize(minHeight = 44.dp).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = if (selected) Color.White else ink, fontSize = 14.sp))
    }
}
