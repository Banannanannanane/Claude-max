package com.mammouthclient.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mammouthclient.app.ui.ChatScreen
import com.mammouthclient.app.ui.ChatViewModel
import com.mammouthclient.app.ui.SettingsScreen
import com.mammouthclient.app.ui.WebAppScreen
import com.mammouthclient.app.ui.theme.MammouthTheme

private enum class Screen { Chat, Settings, Web }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MammouthTheme {
                MammouthApp()
            }
        }
    }
}

@Composable
private fun MammouthApp() {
    val viewModel: ChatViewModel = viewModel()
    var screen by remember { mutableStateOf(Screen.Chat) }

    when (screen) {
        Screen.Chat -> ChatScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.Settings },
            onOpenWeb = { screen = Screen.Web }
        )

        Screen.Settings -> {
            BackHandler { screen = Screen.Chat }
            SettingsScreen(
                viewModel = viewModel,
                onBack = { screen = Screen.Chat }
            )
        }

        Screen.Web -> WebAppScreen(onBack = { screen = Screen.Chat })
    }
}
