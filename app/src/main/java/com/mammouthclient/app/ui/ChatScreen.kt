package com.mammouthclient.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
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
    onOpenImages: () -> Unit,
    onOpenPersonas: () -> Unit,
    onOpenProfile: () -> Unit
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
    var overflowOpen by remember { mutableStateOf(false) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var promptSheetOpen by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf<ModelPickerMode?>(null) }
    var compareOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // L'auto-défilement s'arrête dès que l'utilisateur remonte dans l'historique.
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

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
        if (state.messages.isNotEmpty() && atBottom) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
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
                    onArchive = viewModel::toggleArchive,
                    onToggleArchived = viewModel::toggleShowArchived,
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
                    onOpenPersonas = {
                        scope.launch { drawerState.close() }
                        onOpenPersonas()
                    },
                    onOpenProfile = {
                        scope.launch { drawerState.close() }
                        onOpenProfile()
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
            floatingActionButton = {
                if (!atBottom && state.messages.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(state.messages.lastIndex) } }
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aller en bas")
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier.clickable {
                                modelPicker = ModelPickerMode.Select
                            }
                        ) {
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
                                    val tokens = state.current?.totalTokens ?: 0
                                    if (settings.showUsage && tokens > 0) append(" · $tokens jetons")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
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
                                text = { Text(if (state.current?.archived == true) "Désarchiver" else "Archiver") },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    state.current?.let { viewModel.toggleArchive(it.id) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Comparer des modèles") },
                                leadingIcon = { Icon(Icons.Default.CompareArrows, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    compareOpen = true
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
                    personaLabel = state.personaOf(state.current)?.let { "${it.emoji} ${it.name}" },
                    profileActive = state.profile.enabled && !state.profile.isEmpty,
                    onToggleWeb = viewModel::toggleWebSearch,
                    onClearAssistant = { viewModel.applyAssistant(null) },
                    onOpenAssistants = onOpenAssistants,
                    onClearPersona = { viewModel.applyPersona(null) },
                    onOpenPersonas = onOpenPersonas,
                    onOpenProfile = onOpenProfile,
                    onCompare = { compareOpen = true }
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
                                isLastAssistant = isLast && message.role == Message.ROLE_ASSISTANT,
                                showUsage = settings.showUsage,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(message.content))
                                    viewModel.notify("Copié")
                                },
                                onShare = { share(message.content) },
                                onSpeak = {
                                    tts?.speak(
                                        message.content.take(3500),
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        message.id
                                    )
                                },
                                onRegenerate = viewModel::regenerate,
                                onRegenerateWith = { modelPicker = ModelPickerMode.Regenerate },
                                onContinue = viewModel::continueResponse,
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

    modelPicker?.let { mode ->
        ModelPickerDialog(
            models = state.availableModels,
            favorites = settings.favoriteModels,
            current = state.current?.model ?: settings.model,
            loading = state.modelsLoading,
            onToggleFavorite = viewModel::toggleFavoriteModel,
            onRefresh = viewModel::refreshModels,
            onPick = { model ->
                when (mode) {
                    ModelPickerMode.Select -> viewModel.selectModel(model)
                    ModelPickerMode.Regenerate -> viewModel.regenerateWith(model)
                }
                modelPicker = null
            },
            onDismiss = { modelPicker = null }
        )
    }

    if (compareOpen) {
        CompareDialog(
            models = state.availableModels,
            favorites = settings.favoriteModels,
            initialPrompt = input,
            onDismiss = { compareOpen = false },
            onLaunch = { prompt, models ->
                viewModel.compareModels(prompt, models)
                input = ""
                compareOpen = false
            }
        )
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

private enum class ModelPickerMode { Select, Regenerate }

@Composable
private fun ModelPickerDialog(
    models: List<String>,
    favorites: Set<String>,
    current: String,
    loading: Boolean,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val ordered = remember(models, favorites, query) {
        models
            .filter { query.isBlank() || it.contains(query, true) }
            .sortedWith(compareByDescending<String> { it in favorites }.thenBy { it })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir un modèle") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filtrer…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Chargement…", style = MaterialTheme.typography.labelMedium)
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(ordered, key = { it }) { model ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(model) }
                                .padding(vertical = 4.dp)
                        ) {
                            IconButton(onClick = { onToggleFavorite(model) }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (model in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favori",
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (model == current) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) { Text("Actualiser") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun CompareDialog(
    models: List<String>,
    favorites: Set<String>,
    initialPrompt: String,
    onDismiss: () -> Unit,
    onLaunch: (String, List<String>) -> Unit
) {
    var prompt by remember { mutableStateOf(initialPrompt) }
    var selected by remember { mutableStateOf(favorites.take(2).toSet()) }
    val ordered = remember(models, favorites) {
        models.sortedWith(compareByDescending<String> { it in favorites }.thenBy { it })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comparer des modèles") },
        text = {
            Column {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Question") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Sélectionnez 2 à 4 modèles ; les réponses arrivent l'une après l'autre.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(ordered, key = { it }) { model ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (model in selected) {
                                        selected - model
                                    } else if (selected.size < 4) {
                                        selected + model
                                    } else {
                                        selected
                                    }
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (model in selected) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(model, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLaunch(prompt, selected.toList()) },
                enabled = prompt.isNotBlank() && selected.size >= 2
            ) { Text("Lancer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ConversationDrawer(
    state: ChatUiState,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPin: (String) -> Unit,
    onArchive: (String) -> Unit,
    onToggleArchived: () -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (String) -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenImages: () -> Unit,
    onOpenPersonas: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (state.showArchived) "Archivées" else "Mammouth",
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

        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nouvelle")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleArchived) {
                Icon(
                    imageVector = if (state.showArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.showArchived) "Actives" else "Archives (${state.archivedCount})")
            }
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
                                    text = { Text(if (conversation.archived) "Désarchiver" else "Archiver") },
                                    onClick = {
                                        menuOpen = false
                                        onArchive(conversation.id)
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
        DrawerLink(Icons.Default.Person, "Mon profil", onOpenProfile)
        DrawerLink(Icons.Default.TheaterComedy, "Personas", onOpenPersonas)
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
    personaLabel: String?,
    profileActive: Boolean,
    onToggleWeb: () -> Unit,
    onClearAssistant: () -> Unit,
    onOpenAssistants: () -> Unit,
    onClearPersona: () -> Unit,
    onOpenPersonas: () -> Unit,
    onOpenProfile: () -> Unit,
    onCompare: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = webSearch,
                onClick = onToggleWeb,
                label = { Text("Recherche web") },
                leadingIcon = {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
        item {
            if (assistantLabel != null) {
                AssistChip(
                    onClick = onClearAssistant,
                    label = { Text(assistantLabel, maxLines = 1) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
            } else {
                AssistChip(
                    onClick = onOpenAssistants,
                    label = { Text("Projet") },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        item {
            if (personaLabel != null) {
                AssistChip(
                    onClick = onClearPersona,
                    label = { Text(personaLabel, maxLines = 1) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
            } else {
                AssistChip(
                    onClick = onOpenPersonas,
                    label = { Text("Persona") },
                    leadingIcon = {
                        Icon(Icons.Default.TheaterComedy, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        item {
            AssistChip(
                onClick = onOpenProfile,
                label = { Text(if (profileActive) "Profil actif" else "Profil") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
        item {
            AssistChip(
                onClick = onCompare,
                label = { Text("Comparer") },
                leadingIcon = {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                }
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
                    "Joignez des images ou des PDF, activez la recherche web, comparez plusieurs modèles…"
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
                Column(modifier = Modifier.width(120.dp)) {
                    if (attachment.kind == AttachmentKind.IMAGE && attachment.path.isNotBlank()) {
                        MarkdownImage(attachment.path)
                    }
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (attachment.kind == AttachmentKind.IMAGE) {
                                Icons.Default.Image
                            } else {
                                Icons.Default.Description
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = attachment.name.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemove(attachment.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.size(13.dp))
                        }
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
    isLastAssistant: Boolean,
    showUsage: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit,
    onRegenerateWith: () -> Unit,
    onContinue: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isUser = message.role == Message.ROLE_USER
    var moreOpen by remember(message.id) { mutableStateOf(false) }

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
                    // Texte sélectionnable (copie partielle).
                    SelectionContainer {
                        MarkdownText(text = message.content, color = foreground)
                    }
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
                        val label = when {
                            !isUser && showUsage && message.usage?.isEmpty == false ->
                                "${message.model.take(14)} · ${message.usage.totalTokens} j."

                            !isUser && message.model.isNotBlank() -> message.model.take(20)
                            else -> ""
                        }
                        if (label.isNotBlank()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = foreground,
                                maxLines = 1,
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

                        Box {
                            BubbleAction(Icons.Default.MoreVert, "Plus") { moreOpen = true }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                if (!isUser) {
                                    DropdownMenuItem(
                                        text = { Text("Régénérer avec un autre modèle") },
                                        leadingIcon = {
                                            Icon(Icons.Default.CompareArrows, contentDescription = null)
                                        },
                                        onClick = {
                                            moreOpen = false
                                            onRegenerateWith()
                                        }
                                    )
                                    if (isLastAssistant) {
                                        DropdownMenuItem(
                                            text = { Text("Continuer la réponse") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                moreOpen = false
                                                onContinue()
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text("Partager") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        moreOpen = false
                                        onShare()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        moreOpen = false
                                        onDelete()
                                    }
                                )
                            }
                        }
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
