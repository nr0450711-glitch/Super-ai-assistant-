package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.JarvisApp
import com.example.ui.JarvisViewModel
import com.example.ui.theme.JarvisVipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: JarvisViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            JarvisVipTheme(darkTheme = settings.forceDarkHud) {
                JarvisApp(viewModel = viewModel)
            }
        }
    }
}
