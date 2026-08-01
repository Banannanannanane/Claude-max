package io.github.banannanannanane.mammouth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.banannanannanane.mammouth.R
import io.github.banannanannanane.mammouth.data.ChatMessage
import io.github.banannanannanane.mammouth.data.Conversation
import io.github.banannanannanane.mammouth.data.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversation: Conversation?,
    streaming: StreamingTurn?,
    uiState: ChatUiState,
    modelId: String,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewConversation: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onConsumeError: () -> Unit,
    onConsumeNotice: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeError()
        }
    }
    LaunchedEffect(uiState.notice) {
        uiState.notice?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.conversations))
                    }
                },
                title = {
                    Text(
                        text = conversation?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onNewConversation) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.new_chat))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                value = uiState.input,
                busy = streaming != null,
                onValueChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ModelBar(
                modelId = modelId,
                canRegenerate = conversation?.messages?.any { it.role == Role.USER } == true &&
                    streaming == null,
                onClick = {
                    showModelPicker = true
                    onRefreshModels()
                },
                onRegenerate = onRegenerate,
            )
            HorizontalDivider()

            val messages = conversation?.messages.orEmpty()
            if (messages.isEmpty() && streaming == null) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                MessageList(messages = messages, streaming = streaming)
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = uiState.models,
            loading = uiState.modelsLoading,
            selectedId = modelId,
            onSelect = {
                onSelectModel(it)
                showModelPicker = false
            },
            onRefresh = onRefreshModels,
            onDismiss = { showModelPicker = false },
        )
    }
}

@Composable
private fun ModelBar(
    modelId: String,
    canRegenerate: Boolean,
    onClick: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AssistChip(
            onClick = onClick,
            label = { Text(modelId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.widthIn(max = 260.dp),
        )
        if (canRegenerate) {
            IconButton(onClick = onRegenerate) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.regenerate))
            }
        }
    }
}

@Composable
private fun MessageList(messages: List<ChatMessage>, streaming: StreamingTurn?) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streaming?.text?.length) {
        val lastIndex = messages.size - if (streaming == null) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex.coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message)
        }
        if (streaming != null) {
            item(key = "streaming") {
                MessageBubble(
                    message = ChatMessage(
                        id = "streaming",
                        role = Role.ASSISTANT,
                        content = streaming.text,
                        model = streaming.model,
                    ),
                    pending = true,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, pending: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == Role.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = when {
                message.error != null -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            modifier = if (isUser) Modifier.fillMaxWidth(0.9f) else Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.content.isNotBlank()) {
                    if (isUser) {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        MarkdownText(
                            text = message.content,
                            color = if (message.error != null) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (pending && message.content.isBlank()) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }

                message.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = if (message.content.isBlank()) 0.dp else 8.dp),
                    )
                }
            }
        }

        if (!isUser && !pending && message.content.isNotBlank()) {
            val copiedLabel = stringResource(R.string.copy)
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(message.content)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = copiedLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.empty_chat_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.empty_chat_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.message_hint)) },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp),
            )
            FilledIconButton(
                onClick = { if (busy) onStop() else onSend() },
                enabled = busy || value.isNotBlank(),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(if (busy) R.string.stop else R.string.send),
                )
            }
        }
    }
}
