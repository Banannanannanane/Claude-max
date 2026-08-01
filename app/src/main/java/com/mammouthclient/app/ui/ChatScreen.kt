package com.mammouthclient.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mammouthclient.app.data.Attachment
import com.mammouthclient.app.data.AttachmentKind
import com.mammouthclient.app.data.Conversation
import com.mammouthclient.app.data.Message
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenImages: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var promptSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Synthèse vocale (lecture des réponses).
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { }
        engine.language = Locale.FRANCE
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> viewModel.attach(uris) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.attach(uris) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) input = if (input.isBlank()) spoken else "$input $spoken"
        }
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    LaunchedEffect(state.stagedInput) {
        state.stagedInput?.let { staged ->
            input = if (input.isBlank()) staged else "$input\n$staged"
            viewModel.consumeStagedInput()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it.take(220))
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.info) {
        state.info?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Partager")) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ConversationDrawer(
                    state = state,
                    onNew = {
                        viewModel.newConversation()
                        scope.launch { drawerState.close() }
                    },
                    onSelect = {
                        viewModel.selectConversation(it)
                        scope.launch { drawerState.close() }
                    },
                    onSearch = viewModel::setSearch,
                    onPin = viewModel::togglePin,
                    onRename = { renaming = it },
                    onDelete = viewModel::deleteConversation,
                    onOpenAssistants = {
                        scope.launch { drawerState.close() }
                        onOpenAssistants()
                    },
                    onOpenPrompts = {
                        scope.launch { drawerState.close() }
                        onOpenPrompts()
                    },
                    onOpenImages = {
                        scope.launch { drawerState.close() }
                        onOpenImages()
                    },
                    onOpenWeb = {
                        scope.launch { drawerState.close() }
                        onOpenWeb()
                    },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.clickable { modelMenuOpen = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.current?.title?.take(22) ?: "Mammouth",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Icon(Icons.Default.ExpandMore, contentDescription = "Modèle")
                            }
                            Text(
                                text = buildString {
                                    append(
                                        if (state.current?.webSearch == true) settings.searchModel
                                        else state.current?.model ?: settings.model
                                    )
                                    state.assistantOf(state.current)?.let { append(" · ${it.emoji} ${it.name}") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )

                            DropdownMenu(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false }
                            ) {
                                if (state.modelsLoading) {
                                    DropdownMenuItem(
                                        text = { Text("Chargement des modèles…") },
                                        onClick = {},
                                        enabled = false
                                    )
                                }
                                state.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            viewModel.selectModel(model)
                                            modelMenuOpen = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Actualiser la liste") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        viewModel.refreshModels()
                                        modelMenuOpen = false
                                    }
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Discussions")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.newConversation() }) {
                            Icon(Icons.Default.Add, contentDescription = "Nouvelle discussion")
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renommer") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    renaming = state.current
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.current?.pinned == true) "Désépingler" else "Épingler") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    state.current?.let { viewModel.togglePin(it.id) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Partager la discussion") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    share(viewModel.exportCurrentAsMarkdown())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Projets") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    onOpenAssistants()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Réglages") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    onOpenSettings()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Supprimer la discussion") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    state.current?.let { viewModel.deleteConversation(it.id) }
                                }
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                ContextChips(
                    webSearch = state.current?.webSearch == true,
                    assistantLabel = state.assistantOf(state.current)?.let { "${it.emoji} ${it.name}" },
                    onToggleWeb = viewModel::toggleWebSearch,
                    onClearAssistant = { viewModel.applyAssistant(null) },
                    onOpenAssistants = onOpenAssistants
                )

                if (state.messages.isEmpty()) {
                    EmptyState(
                        hasApiKey = settings.hasApiKey,
                        onOpenSettings = onOpenSettings,
                        onOpenPrompts = onOpenPrompts,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            val isLast = message.id == state.messages.lastOrNull()?.id
                            MessageBubble(
                                message = message,
                                isStreamingLast = state.isStreaming && isLast,
                                showUsage = settings.showUsage,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(message.content))
                                    viewModel.notify("Copié")
                                },
                                onShare = { share(message.content) },
                                onSpeak = {
                                    tts?.speak(message.content.take(3500), TextToSpeech.QUEUE_FLUSH, null, message.id)
                                },
                                onRegenerate = viewModel::regenerate,
                                onEdit = { editing = message },
                                onDelete = { viewModel.deleteMessage(message.id) }
                            )
                        }
                    }
                }

                if (state.pendingAttachments.isNotEmpty() || state.importing) {
                    AttachmentStrip(
                        attachments = state.pendingAttachments,
                        importing = state.importing,
                        onRemove = viewModel::removeAttachment
                    )
                }

                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    isStreaming = state.isStreaming,
                    attachMenuOpen = attachMenuOpen,
                    onAttachMenu = { attachMenuOpen = it },
                    onPickImages = { imagePicker.launch("image/*") },
                    onPickDocuments = {
                        documentPicker.launch(
                            arrayOf(
                                "application/pdf",
                                "text/*",
                                "application/json",
                                "text/csv",
                                "application/octet-stream"
                            )
                        )
                    },
                    onVoice = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRANCE.toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictez votre message")
                        }
                        runCatching { speechLauncher.launch(intent) }
                            .onFailure { viewModel.notify("Dictée vocale indisponible sur cet appareil.") }
                    },
                    onPrompts = { promptSheetOpen = true },
                    onSend = {
                        viewModel.send(input)
                        input = ""
                    },
                    onStop = viewModel::stop
                )
            }
        }
    }

    if (promptSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { promptSheetOpen = false },
            sheetState = sheetState
        ) {
            Text(
                text = "Bibliothèque de prompts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(state.prompts, key = { it.id }) { prompt ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(prompt.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    prompt.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            input = if (input.isBlank()) prompt.content else input + "\n" + prompt.content
                            promptSheetOpen = false
                        }
                    )
                }
            }
            TextButton(
                onClick = {
                    promptSheetOpen = false
                    onOpenPrompts()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Gérer mes prompts") }
        }
    }

    renaming?.let { conversation ->
        var title by remember(conversation.id) { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renommer") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameConversation(conversation.id, title)
                    renaming = null
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Annuler") }
            }
        )
    }

    editing?.let { message ->
        var draft by remember(message.id) { mutableStateOf(message.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Modifier et renvoyer") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.editAndResend(message.id, draft)
                    editing = null
                }) { Text("Renvoyer") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun ConversationDrawer(
    state: ChatUiState,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPin: (String) -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (String) -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenImages: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Mammouth",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp)
        )

        OutlinedTextField(
            value = state.search,
            onValueChange = onSearch,
            placeholder = { Text("Rechercher…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        TextButton(onClick = onNew, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Nouvelle discussion")
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.visibleConversations, key = { it.id }) { conversation ->
                var menuOpen by remember(conversation.id) { mutableStateOf(false) }
                NavigationDrawerItem(
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (conversation.pinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(conversation.title, maxLines = 1)
                        }
                    },
                    selected = conversation.id == state.currentId,
                    onClick = { onSelect(conversation.id) },
                    badge = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (conversation.pinned) "Désépingler" else "Épingler") },
                                    onClick = {
                                        menuOpen = false
                                        onPin(conversation.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Renommer") },
                                    onClick = {
                                        menuOpen = false
                                        onRename(conversation)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer") },
                                    onClick = {
                                        menuOpen = false
                                        onDelete(conversation.id)
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }

        HorizontalDivider()
        DrawerLink(Icons.Default.Folder, "Projets", onOpenAssistants)
        DrawerLink(Icons.Default.Description, "Prompts", onOpenPrompts)
        DrawerLink(Icons.Default.Image, "Atelier d'images", onOpenImages)
        DrawerLink(Icons.Default.Language, "App web Mammouth", onOpenWeb)
        DrawerLink(Icons.Default.Settings, "Réglages", onOpenSettings)
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun DrawerLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextChips(
    webSearch: Boolean,
    assistantLabel: String?,
    onToggleWeb: () -> Unit,
    onClearAssistant: () -> Unit,
    onOpenAssistants: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = webSearch,
            onClick = onToggleWeb,
            label = { Text("Recherche web") },
            leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        if (assistantLabel != null) {
            AssistChip(
                onClick = onClearAssistant,
                label = { Text(assistantLabel, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
        } else {
            AssistChip(
                onClick = onOpenAssistants,
                label = { Text("Projet") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun EmptyState(
    hasApiKey: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPrompts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Text("🐘", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.size(12.dp))
            Text(
                text = if (hasApiKey) "Posez votre question" else "Bienvenue",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (hasApiKey) {
                    "Choisissez un modèle en haut, joignez des images ou des PDF, activez la recherche web…"
                } else {
                    "Ajoutez votre clé API Mammouth pour commencer."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(16.dp))
            if (hasApiKey) {
                TextButton(onClick = onOpenPrompts) { Text("Parcourir les prompts") }
            } else {
                TextButton(onClick = onOpenSettings) { Text("Ouvrir les réglages") }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<Attachment>,
    importing: Boolean,
    onRemove: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (importing) {
            item {
                Card {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Import…", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        items(attachments, key = { it.id }) { attachment ->
            Card {
                Row(
                    modifier = Modifier.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (attachment.kind == AttachmentKind.IMAGE) {
                            Icons.Default.Image
                        } else {
                            Icons.Default.Description
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = attachment.name.take(22),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                    IconButton(onClick = { onRemove(attachment.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isStreamingLast: Boolean,
    showUsage: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isUser = message.role == Message.ROLE_USER
    val background = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = background,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                message.attachments.forEach { attachment ->
                    if (attachment.kind == AttachmentKind.IMAGE && attachment.path.isNotBlank()) {
                        MarkdownImage(attachment.path, modifier = Modifier.padding(bottom = 6.dp))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(attachment.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (message.content.isBlank() && isStreamingLast) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Rédaction…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (message.content.isNotBlank()) {
                    MarkdownText(text = message.content, color = foreground)
                }

                message.images.forEach { path ->
                    MarkdownImage(path, modifier = Modifier.padding(top = 6.dp))
                }

                if (!isStreamingLast) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!isUser && showUsage && message.usage?.isEmpty == false) {
                            Text(
                                text = "${message.usage.totalTokens} jetons",
                                style = MaterialTheme.typography.labelSmall,
                                color = foreground,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (!isUser && message.model.isNotBlank()) {
                            Text(
                                text = message.model,
                                style = MaterialTheme.typography.labelSmall,
                                color = foreground,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        BubbleAction(Icons.Default.ContentCopy, "Copier", onCopy)
                        if (isUser) {
                            BubbleAction(Icons.Default.Edit, "Modifier", onEdit)
                        } else {
                            BubbleAction(Icons.Default.VolumeUp, "Lire", onSpeak)
                            BubbleAction(Icons.Default.Refresh, "Régénérer", onRegenerate)
                        }
                        BubbleAction(Icons.Default.Share, "Partager", onShare)
                        BubbleAction(Icons.Default.Delete, "Supprimer", onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isStreaming: Boolean,
    attachMenuOpen: Boolean,
    onAttachMenu: (Boolean) -> Unit,
    onPickImages: () -> Unit,
    onPickDocuments: () -> Unit,
    onVoice: () -> Unit,
    onPrompts: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box {
                IconButton(onClick = { onAttachMenu(true) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Joindre")
                }
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { onAttachMenu(false) }) {
                    DropdownMenuItem(
                        text = { Text("Images") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            onAttachMenu(false)
                            onPickImages()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Document (PDF, texte…)") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        onClick = {
                            onAttachMenu(false)
                            onPickDocuments()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bibliothèque de prompts") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        onClick = {
                            onAttachMenu(false)
                            onPrompts()
                        }
                    )
                }
            }

            IconButton(onClick = onVoice) {
                Icon(Icons.Default.Mic, contentDescription = "Dicter")
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 160.dp),
                placeholder = { Text("Votre message…") },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = { if (isStreaming) onStop() else onSend() },
                    enabled = isStreaming || value.isNotBlank()
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.Send,
                        contentDescription = if (isStreaming) "Arrêter" else "Envoyer",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
