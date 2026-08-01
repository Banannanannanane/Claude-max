package com.mammouthclient.app.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Atelier d'images : génération, aperçu et export en galerie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    viewModel: ImageViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onShare: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val chatState by chatViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(chatState.availableModels) {
        viewModel.offerModels(chatState.availableModels)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Atelier d'images") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = viewModel::setPrompt,
                    label = { Text("Décrivez l'image") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Modèle", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.availableModels) { model ->
                        FilterChip(
                            selected = model == state.model,
                            onClick = { viewModel.setModel(model) },
                            label = { Text(model) }
                        )
                    }
                }
            }

            item {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ImageUiState.SIZES) { size ->
                        FilterChip(
                            selected = size == state.size,
                            onClick = { viewModel.setSize(size) },
                            label = { Text(size) }
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nombre : ${state.count}", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    (1..4).forEach { value ->
                        FilterChip(
                            selected = value == state.count,
                            onClick = { viewModel.setCount(value) },
                            label = { Text("$value") },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::generate,
                    enabled = !state.isGenerating && state.prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Génération…")
                    } else {
                        Text("Générer")
                    }
                }
            }

            if (state.note.isNotBlank()) {
                item {
                    Card {
                        Text(
                            state.note.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            items(state.results, key = { it }) { path ->
                Card {
                    Column {
                        MarkdownImage(path)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { viewModel.saveToGallery(path) }) {
                                Icon(Icons.Default.Download, contentDescription = "Enregistrer")
                            }
                            IconButton(onClick = { onShare(path) }) {
                                Icon(Icons.Default.Share, contentDescription = "Partager")
                            }
                        }
                    }
                }
            }
        }
    }
}
