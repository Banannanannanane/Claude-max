package com.mammouthclient.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import com.mammouthclient.app.data.Persona

/**
 * Personas : des personnalités d'IA réutilisables, applicables à une discussion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onStartChat: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<Persona?>(null) }
    val activeId = state.current?.personaId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Persona() }) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau persona")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Un persona définit le rôle et le ton de l'IA. Appliquez-le à la discussion " +
                        "courante ou démarrez-en une nouvelle avec lui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(state.personas, key = { it.id }) { persona ->
                Card {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(persona.emoji, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(persona.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    buildString {
                                        append(persona.description.ifBlank { "Persona personnalisé" })
                                        if (persona.model.isNotBlank()) append(" · ${persona.model}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!persona.builtIn) {
                                IconButton(onClick = { editing = persona }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Modifier")
                                }
                                IconButton(onClick = { viewModel.deletePersona(persona.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                }
                            }
                        }

                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            TextButton(
                                onClick = {
                                    viewModel.applyPersona(
                                        if (activeId == persona.id) null else persona.id
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    if (activeId == persona.id) {
                                        "Retirer de la discussion"
                                    } else {
                                        "Appliquer ici"
                                    }
                                )
                            }
                            TextButton(onClick = { onStartChat(persona.id) }) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("Nouvelle discussion")
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { persona ->
        PersonaEditor(
            initial = persona,
            models = state.availableModels,
            onDismiss = { editing = null },
            onSave = {
                viewModel.savePersona(it)
                editing = null
            }
        )
    }
}

@Composable
private fun PersonaEditor(
    initial: Persona,
    models: List<String>,
    onDismiss: () -> Unit,
    onSave: (Persona) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Nouveau persona" else "Modifier le persona") },
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
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it) },
                    label = { Text("Description courte") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.systemPrompt,
                    onValueChange = { draft = draft.copy(systemPrompt = it) },
                    label = { Text("Comportement (instruction système)") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    label = { Text("Modèle préféré (facultatif)") },
                    singleLine = true,
                    supportingText = { Text(models.take(4).joinToString(", ").take(90)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.name.isNotBlank() && draft.systemPrompt.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
