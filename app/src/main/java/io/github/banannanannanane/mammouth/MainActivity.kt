package io.github.banannanannanane.mammouth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.banannanannanane.mammouth.ui.ChatViewModel
import io.github.banannanannanane.mammouth.ui.MammouthApp
import io.github.banannanannanane.mammouth.ui.theme.MammouthTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MammouthTheme {
                val viewModel: ChatViewModel = viewModel()
                MammouthApp(viewModel)
            }
        }
    }
}
