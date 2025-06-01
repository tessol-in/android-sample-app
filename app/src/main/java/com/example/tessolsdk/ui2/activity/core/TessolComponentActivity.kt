package com.example.tessolsdk.ui2.activity.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.tessolsdk.ui2.theme.TessolTheme

abstract class TessolComponentActivity : ComponentActivity() {
    @Composable
    abstract fun ComposableContent()

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TessolTheme {
                Surface(Modifier.fillMaxSize()) {
                    ComposableContent()
                }
            }
        }
    }
}