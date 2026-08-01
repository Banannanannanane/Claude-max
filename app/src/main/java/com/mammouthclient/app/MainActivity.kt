package com.mammouthclient.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mammouthclient.app.ui.AssistantsScreen
import com.mammouthclient.app.ui.ChatScreen
import com.mammouthclient.app.ui.ChatViewModel
import com.mammouthclient.app.ui.ImageScreen
import com.mammouthclient.app.ui.ImageViewModel
import com.mammouthclient.app.ui.PersonasScreen
import com.mammouthclient.app.ui.ProfileScreen
import com.mammouthclient.app.ui.PromptsScreen
import com.mammouthclient.app.ui.SettingsScreen
import com.mammouthclient.app.ui.WebAppScreen
import com.mammouthclient.app.ui.theme.MammouthTheme
import java.io.File

private enum class Screen { Chat, Settings, Web, Assistants, Prompts, Images, Personas, Profile }

/** Contenu reçu depuis une autre application (partage entrant). */
private data class SharedPayload(
    val text: String = "",
    val uris: List<Uri> = emptyList()
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val payload = readSharedPayload(intent)
        val startScreen = when (intent?.getStringExtra(EXTRA_SHORTCUT)) {
            SHORTCUT_IMAGES -> Screen.Images
            SHORTCUT_PROJECTS -> Screen.Assistants
            else -> Screen.Chat
        }

        setContent {
            MammouthApp(payload, startScreen)
        }
    }

    @Suppress("DEPRECATION")
    private fun readSharedPayload(intent: Intent?): SharedPayload = when (intent?.action) {
        Intent.ACTION_SEND -> {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            SharedPayload(
                text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                uris = listOfNotNull(uri)
            )
        }

        Intent.ACTION_SEND_MULTIPLE -> SharedPayload(
            uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        )

        Intent.ACTION_PROCESS_TEXT -> SharedPayload(
            text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        )

        else -> SharedPayload()
    }

    companion object {
        const val EXTRA_SHORTCUT = "shortcut"
        const val SHORTCUT_IMAGES = "images"
        const val SHORTCUT_PROJECTS = "projects"
    }
}

@Composable
private fun MammouthApp(payload: SharedPayload, startScreen: Screen) {
    val context = LocalContext.current
    val chatViewModel: ChatViewModel = viewModel()
    val imageViewModel: ImageViewModel = viewModel()
    val settings by chatViewModel.settings.collectAsState()
    var screen by remember {
        mutableStateOf(if (settings.startOnWeb && startScreen == Screen.Chat) Screen.Web else startScreen)
    }

    var unlocked by remember { mutableStateOf(!settings.appLock) }
    var lockFailed by remember { mutableStateOf(false) }
    val lockLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        unlocked = result.resultCode == Activity.RESULT_OK
        lockFailed = !unlocked
    }

    fun requestUnlock() {
        val keyguard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(KeyguardManager::class.java)
        } else {
            null
        }
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "Mammouth",
            "Déverrouillez pour accéder à vos discussions"
        )
        if (intent == null) {
            // Aucun code d'écran configuré : on n'enferme pas l'utilisateur.
            unlocked = true
        } else {
            lockFailed = false
            runCatching { lockLauncher.launch(intent) }.onFailure { unlocked = true }
        }
    }

    LaunchedEffect(settings.appLock) {
        if (settings.appLock && !unlocked) requestUnlock()
    }

    LaunchedEffect(payload) {
        if (payload.text.isNotBlank()) chatViewModel.stageInput(payload.text)
        if (payload.uris.isNotEmpty()) chatViewModel.attach(payload.uris)
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
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Partager l'image"))
        }
    }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching {
            val uri = if (url.startsWith("http")) {
                Uri.parse(url)
            } else {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(url))
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    MammouthTheme(themeMode = settings.themeMode, fontScale = settings.fontScale) {
        if (!unlocked) {
            LockScreen(failed = lockFailed, onRetry = { requestUnlock() })
            return@MammouthTheme
        }

        when (screen) {
            Screen.Chat -> ChatScreen(
                viewModel = chatViewModel,
                onOpenSettings = { screen = Screen.Settings },
                onOpenWeb = { screen = Screen.Web },
                onOpenAssistants = { screen = Screen.Assistants },
                onOpenPrompts = { screen = Screen.Prompts },
                onOpenImages = { screen = Screen.Images },
                onOpenPersonas = { screen = Screen.Personas },
                onOpenProfile = { screen = Screen.Profile }
            )

            Screen.Settings -> {
                BackHandler { screen = Screen.Chat }
                SettingsScreen(
                    viewModel = chatViewModel,
                    onBack = { screen = Screen.Chat },
                    onExport = { shareText(it) },
                    onOpenProfile = { screen = Screen.Profile }
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
                    onShareFile = { shareFile(it) },
                    onOpenUrl = { openUrl(it) }
                )
            }

            Screen.Personas -> {
                BackHandler { screen = Screen.Chat }
                PersonasScreen(
                    viewModel = chatViewModel,
                    onBack = { screen = Screen.Chat },
                    onStartChat = { personaId ->
                        chatViewModel.newConversation(personaId = personaId)
                        screen = Screen.Chat
                    }
                )
            }

            Screen.Profile -> {
                BackHandler { screen = Screen.Chat }
                ProfileScreen(
                    viewModel = chatViewModel,
                    onBack = { screen = Screen.Chat }
                )
            }

            Screen.Web -> WebAppScreen(onBack = { screen = Screen.Chat })
        }
    }
}

@Composable
private fun LockScreen(failed: Boolean, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔒", style = MaterialTheme.typography.displayMedium)
            Text(
                text = "Application verrouillée",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = if (failed) {
                    "Déverrouillage annulé."
                } else {
                    "Utilisez le code ou la biométrie de l'appareil."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
                Text("Déverrouiller")
            }
        }
    }
}
