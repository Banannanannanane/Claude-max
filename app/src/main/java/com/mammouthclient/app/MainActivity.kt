package com.mammouthclient.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mammouthclient.app.ui.AssistantsScreen
import com.mammouthclient.app.ui.ChatScreen
import com.mammouthclient.app.ui.ChatViewModel
import com.mammouthclient.app.ui.ImageScreen
import com.mammouthclient.app.ui.ImageViewModel
import com.mammouthclient.app.ui.PromptsScreen
import com.mammouthclient.app.ui.SettingsScreen
import com.mammouthclient.app.ui.WebAppScreen
import com.mammouthclient.app.ui.theme.MammouthTheme
import java.io.File

private enum class Screen { Chat, Settings, Web, Assistants, Prompts, Images }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()

            else -> ""
        }

        setContent {
            MammouthApp(sharedText)
        }
    }
}

@Composable
private fun MammouthApp(sharedText: String) {
    val context = LocalContext.current
    val chatViewModel: ChatViewModel = viewModel()
    val imageViewModel: ImageViewModel = viewModel()
    val settings by chatViewModel.settings.collectAsState()
    var screen by remember { mutableStateOf(Screen.Chat) }

    LaunchedEffect(sharedText) {
        if (sharedText.isNotBlank()) chatViewModel.stageInput(sharedText)
    }

    fun shareText(text: String) {
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Partager")) }
    }

    fun shareFile(path: String) {
        runCatching {
            val file = File(path)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Partager l'image"))
        }
    }

    MammouthTheme(themeMode = settings.themeMode, fontScale = settings.fontScale) {
        when (screen) {
            Screen.Chat -> ChatScreen(
                viewModel = chatViewModel,
                onOpenSettings = { screen = Screen.Settings },
                onOpenWeb = { screen = Screen.Web },
                onOpenAssistants = { screen = Screen.Assistants },
                onOpenPrompts = { screen = Screen.Prompts },
                onOpenImages = { screen = Screen.Images }
            )

            Screen.Settings -> {
                BackHandler { screen = Screen.Chat }
                SettingsScreen(
                    viewModel = chatViewModel,
                    onBack = { screen = Screen.Chat },
                    onExport = { shareText(it) }
                )
            }

            Screen.Assistants -> {
                BackHandler { screen = Screen.Chat }
                AssistantsScreen(
                    viewModel = chatViewModel,
                    onBack = { screen = Screen.Chat },
                    onStartChat = { assistantId ->
                        chatViewModel.newConversation(assistantId = assistantId)
                        screen = Screen.Chat
                    }
                )
            }

            Screen.Prompts -> {
                BackHandler { screen = Screen.Chat }
                PromptsScreen(
                    viewModel = chatViewModel,
                    onBack = { screen = Screen.Chat },
                    onUse = { content ->
                        chatViewModel.stageInput(content)
                        screen = Screen.Chat
                    }
                )
            }

            Screen.Images -> {
                BackHandler { screen = Screen.Chat }
                ImageScreen(
                    viewModel = imageViewModel,
                    chatViewModel = chatViewModel,
                    onBack = { screen = Screen.Chat },
                    onShare = { shareFile(it) }
                )
            }

            Screen.Web -> WebAppScreen(onBack = { screen = Screen.Chat })
        }
    }
}
