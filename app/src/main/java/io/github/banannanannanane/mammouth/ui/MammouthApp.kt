package io.github.banannanannanane.mammouth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** Root of the UI: conversation drawer, chat surface and the settings screen. */
@Composable
fun MammouthApp(viewModel: ChatViewModel) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val streaming by viewModel.streaming.collectAsState()

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            settings = settings,
            uiState = uiState,
            onBack = { showSettings = false },
            onApiKeyChange = viewModel::setApiKey,
            onBaseUrlChange = viewModel::setBaseUrl,
            onSystemPromptChange = viewModel::setSystemPrompt,
            onTemperatureChange = viewModel::setTemperature,
            onStreamingChange = viewModel::setStreaming,
            onSelectModel = viewModel::selectModel,
            onRefreshModels = viewModel::refreshModels,
            onTestConnection = viewModel::testConnection,
            onDeleteAllConversations = viewModel::deleteAllConversations,
            onConsumeNotice = viewModel::consumeNotice,
        )
        return
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ConversationDrawer(
                    conversations = conversations,
                    currentId = uiState.currentConversationId,
                    onNewConversation = {
                        viewModel.newConversation()
                        scope.launch { drawerState.close() }
                    },
                    onSelect = {
                        viewModel.selectConversation(it)
                        scope.launch { drawerState.close() }
                    },
                    onDelete = viewModel::deleteConversation,
                    onRename = viewModel::renameConversation,
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        showSettings = true
                    },
                )
            }
        },
    ) {
        val currentConversation = remember(conversations, uiState.currentConversationId) {
            conversations.firstOrNull { it.id == uiState.currentConversationId }
        }

        ChatScreen(
            conversation = currentConversation,
            streaming = streaming,
            uiState = uiState,
            modelId = currentConversation?.model ?: settings.model,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenSettings = { showSettings = true },
            onNewConversation = viewModel::newConversation,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onStop = viewModel::stop,
            onRegenerate = viewModel::regenerate,
            onSelectModel = viewModel::selectModel,
            onRefreshModels = viewModel::refreshModels,
            onConsumeError = viewModel::consumeError,
            onConsumeNotice = viewModel::consumeNotice,
        )
    }
}
