package com.mammouthclient.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mammouthclient.app.data.Assistant
import com.mammouthclient.app.data.AttachmentKind
import com.mammouthclient.app.util.FileUtils
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Projets Mammouth : instructions personnalisées + documents de référence. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onStartChat: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<Assistant?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Assistant() }) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau projet")
            }
        }
    ) { padding ->
        if (state.assistants.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📁", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.size(12.dp))
                Text("Aucun projet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(6.dp))
                Text(
                    "Un projet regroupe des instructions permanentes, un modèle préféré et des documents de référence injectés dans chaque discussion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.assistants, key = { it.id }) { assistant ->
                    Card {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(assistant.emoji, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        assistant.name.ifBlank { "Sans titre" },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        buildString {
                                            append(assistant.model.ifBlank { "modèle par défaut" })
                                            if (assistant.documents.isNotEmpty()) {
                                                append(" · ${assistant.documents.size} document(s)")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editing = assistant }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Modifier")
                                }
                                IconButton(onClick = { viewModel.deleteAssistant(assistant.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                }
                            }
                            if (assistant.instructions.isNotBlank()) {
                                Text(
                                    assistant.instructions.take(160),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            TextButton(
                                onClick = { onStartChat(assistant.id) },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Discuter avec ce projet")
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { assistant ->
        AssistantEditor(
            initial = assistant,
            models = state.availableModels,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveAssistant(it)
                editing = null
            }
        )
    }
}

@Composable
private fun AssistantEditor(
    initial: Assistant,
    models: List<String>,
    onDismiss: () -> Unit,
    onSave: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(initial.id) { mutableStateOf(initial) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val imported = uris.flatMap { uri ->
                    runCatching { FileUtils.importUri(context, uri) }.getOrDefault(emptyList())
                }.filter { it.kind == AttachmentKind.TEXT }
                draft = draft.copy(documents = draft.documents + imported)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Nouveau projet" else "Modifier le projet") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.emoji,
                        onValueChange = { draft = draft.copy(emoji = it.take(2)) },
                        label = { Text("Icône") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text("Nom") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = draft.instructions,
                    onValueChange = { draft = draft.copy(instructions = it) },
                    label = { Text("Instructions permanentes") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    label = { Text("Modèle (vide = par défaut)") },
                    singleLine = true,
                    supportingText = {
                        Text(models.take(4).joinToString(", ").take(90))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(onClick = {
                    documentPicker.launch(arrayOf("text/*", "application/json", "text/csv"))
                }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ajouter un document texte")
                }

                draft.documents.forEach { document ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            document.name.take(28),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            draft = draft.copy(documents = draft.documents.filterNot { it.id == document.id })
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.name.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
