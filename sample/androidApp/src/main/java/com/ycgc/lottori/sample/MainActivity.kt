package com.ycgc.lottori.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleScreen { name ->
                val external = File(filesDir, name)
                if (external.isFile) external.readBytes() else assets.open(name).use { it.readBytes() }
            }
        }
    }
}
