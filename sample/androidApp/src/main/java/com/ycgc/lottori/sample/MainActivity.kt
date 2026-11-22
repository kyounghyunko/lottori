package com.ycgc.lottori.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleApp { name -> File(filesDir, name).takeIf { it.isFile }?.readBytes() }
        }
    }
}
