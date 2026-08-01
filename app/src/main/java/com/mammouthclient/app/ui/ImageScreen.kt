package com.mammouthclient.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.mammouthclient.app.data.GeneratedMedia

/** Atelier visuel : génération, édition d'image, historique, export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    viewModel: ImageViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onShareFile: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val chatState by chatViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::pickSource) }

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
                title = { Text(if (state.isEditing) "Édition d'image" else "Atelier d'images") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = viewModel::setPrompt,
                    label = {
                        Text(if (state.isEditing) "Que modifier sur l'image ?" else "Décrivez l'image")
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { sourcePicker.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isEditing) "Changer l'image source" else "Partir d'une image")
                    }
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::clearSource) {
                            Icon(Icons.Default.Close, contentDescription = "Retirer l'image source")
                        }
                    }
                }
                state.sourcePath?.let { path ->
                    MarkdownImage(path, modifier = Modifier.padding(top = 8.dp))
                }
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

            if (!state.isEditing) {
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
                        Text(if (state.isEditing) "Modifier l'image" else "Générer")
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

            if (state.results.isNotEmpty()) {
                item {
                    Text(
                        "Historique (${state.results.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            items(state.results, key = { it.id }) { media ->
                MediaCard(
                    media = media,
                    onSave = { viewModel.saveToGallery(media) },
                    onShare = { if (media.path.isNotBlank()) onShareFile(media.path) else onOpenUrl(media.url) },
                    onOpen = { onOpenUrl(media.url.ifBlank { media.path }) },
                    onEdit = { viewModel.useAsSource(media) },
                    onDelete = { viewModel.delete(media) }
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    media: GeneratedMedia,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column {
            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎬  Vidéo générée", style = MaterialTheme.typography.titleMedium)
                }
            } else if (media.path.isNotBlank()) {
                MarkdownImage(media.path)
            } else if (media.url.isNotBlank()) {
                MarkdownImage(media.url)
            }

            if (media.prompt.isNotBlank()) {
                Text(
                    media.prompt.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (media.isVideo || media.url.isNotBlank()) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Ouvrir")
                    }
                }
                if (!media.isVideo && media.path.isNotBlank()) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Reprendre comme source")
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Download, contentDescription = "Enregistrer")
                    }
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Partager")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                }
            }
        }
    }
}
