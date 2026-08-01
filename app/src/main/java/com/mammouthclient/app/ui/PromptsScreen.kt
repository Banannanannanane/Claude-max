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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.mammouthclient.app.data.PromptTemplate

/** Bibliothèque de prompts : modèles fournis + prompts personnels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onUse: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<PromptTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = PromptTemplate() }) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau prompt")
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
            items(state.prompts, key = { it.id }) { prompt ->
                Card {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prompt.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    prompt.category + if (prompt.builtIn) " · fourni" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onUse(prompt.content) }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Utiliser")
                            }
                            if (!prompt.builtIn) {
                                IconButton(onClick = { editing = prompt }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Modifier")
                                }
                                IconButton(onClick = { viewModel.deletePrompt(prompt.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                }
                            }
                        }
                        Text(
                            prompt.content.take(140),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    editing?.let { prompt ->
        var draft by remember(prompt.id) { mutableStateOf(prompt) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (prompt.title.isBlank()) "Nouveau prompt" else "Modifier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.title,
                            onValueChange = { draft = draft.copy(title = it) },
                            label = { Text("Titre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.category,
                            onValueChange = { draft = draft.copy(category = it) },
                            label = { Text("Catégorie") },
                            singleLine = true,
                            modifier = Modifier.width(130.dp)
                        )
                    }
                    OutlinedTextField(
                        value = draft.content,
                        onValueChange = { draft = draft.copy(content = it) },
                        label = { Text("Contenu") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.savePrompt(draft)
                        editing = null
                    },
                    enabled = draft.title.isNotBlank() && draft.content.isNotBlank()
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Annuler") }
            }
        )
    }
}
