package com.mammouthclient.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mammouthclient.app.data.AppContainer
import com.mammouthclient.app.data.AppSettings
import com.mammouthclient.app.data.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppContainer.settings(context) }
    val settings by repository.settings.collectAsState()
    val state by viewModel.state.collectAsState()

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var systemPrompt by remember { mutableStateOf(settings.systemPrompt) }
    var temperature by remember { mutableStateOf(settings.temperature) }
    var topP by remember { mutableStateOf(settings.topP) }
    var maxTokens by remember { mutableStateOf(if (settings.maxTokens > 0) settings.maxTokens.toString() else "") }
    var streaming by remember { mutableStateOf(settings.streaming) }
    var searchModel by remember { mutableStateOf(settings.searchModel) }
    var imageModel by remember { mutableStateOf(settings.imageModel) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var fontScale by remember { mutableStateOf(settings.fontScale) }
    var showUsage by remember { mutableStateOf(settings.showUsage) }
    var revealKey by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    fun persist() {
        repository.update {
            it.copy(
                apiKey = apiKey.trim(),
                baseUrl = baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL },
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                streaming = streaming,
                searchModel = searchModel.trim().ifBlank { AppSettings.DEFAULT_SEARCH_MODEL },
                imageModel = imageModel.trim().ifBlank { AppSettings.DEFAULT_IMAGE_MODEL },
                themeMode = themeMode,
                fontScale = fontScale,
                showUsage = showUsage
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { persist() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = {
                        persist()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Connexion à l'API", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Clé API Mammouth") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Afficher/masquer la clé"
                        )
                    }
                },
                supportingText = {
                    Text("Générez la clé depuis votre compte Mammouth (section API). Elle est chiffrée par l'AndroidKeyStore et reste sur l'appareil.")
                }
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("URL de base") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Par défaut : ${AppSettings.DEFAULT_BASE_URL}") }
            )

            Button(
                onClick = {
                    persist()
                    viewModel.refreshModels()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Tester et charger les modèles") }

            if (state.availableModels.isNotEmpty()) {
                Text(
                    "${state.availableModels.size} modèle(s) disponible(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()
            Text("Modèles spécialisés", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = searchModel,
                onValueChange = { searchModel = it },
                label = { Text("Modèle « recherche web »") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Utilisé quand la puce Recherche web est activée (ex. sonar-pro).") }
            )

            OutlinedTextField(
                value = imageModel,
                onValueChange = { imageModel = it },
                label = { Text("Modèle d'images") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Utilisé par l'atelier d'images (ex. gpt-image, flux…).") }
            )

            HorizontalDivider()
            Text("Génération", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("Instruction système globale") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )

            Column {
                Text("Température : ${(temperature * 100).roundToInt() / 100f}")
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    steps = 19
                )
            }

            Column {
                Text("Top-p : ${(topP * 100).roundToInt() / 100f}")
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.1f..1f,
                    steps = 8
                )
            }

            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it.filter { char -> char.isDigit() } },
                label = { Text("Jetons max (0 = illimité)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SwitchRow(
                title = "Réponse en streaming",
                subtitle = "Affiche le texte au fil de l'eau.",
                checked = streaming,
                onCheckedChange = { streaming = it }
            )

            SwitchRow(
                title = "Afficher la consommation",
                subtitle = "Nombre de jetons sous chaque réponse.",
                checked = showUsage,
                onCheckedChange = { showUsage = it }
            )

            HorizontalDivider()
            Text("Apparence", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { themeMode = mode },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Système"
                                    ThemeMode.LIGHT -> "Clair"
                                    ThemeMode.DARK -> "Sombre"
                                }
                            )
                        }
                    )
                }
            }

            Column {
                Text("Taille du texte : ${(fontScale * 100).roundToInt()} %")
                Slider(
                    value = fontScale,
                    onValueChange = { fontScale = it },
                    valueRange = 0.85f..1.4f,
                    steps = 10
                )
            }

            HorizontalDivider()
            Text("Données locales", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(
                onClick = { onExport(viewModel.exportCurrentAsMarkdown()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exporter la discussion courante") }

            OutlinedButton(
                onClick = { confirmWipe = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Supprimer toutes les discussions") }

            Spacer(Modifier.size(4.dp))
            Text(
                "Application non officielle. Les échanges sont envoyés directement à l'API Mammouth avec votre clé ; aucun serveur tiers n'est utilisé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Supprimer les discussions ?") },
            text = { Text("Toutes les conversations enregistrées sur cet appareil seront effacées. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllConversations()
                    confirmWipe = false
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
