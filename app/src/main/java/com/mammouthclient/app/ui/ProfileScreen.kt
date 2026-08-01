package com.mammouthclient.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.mammouthclient.app.data.UserProfile

/**
 * Profil utilisateur : ce que l'IA sait de vous. Injecté au début de chaque discussion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var draft by remember(state.profile) { mutableStateOf(state.profile) }

    fun persist() {
        if (draft != state.profile) viewModel.saveProfile(draft)
    }

    DisposableEffect(Unit) {
        onDispose { persist() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mon profil") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Ces informations sont ajoutées en tête de chaque discussion pour que les réponses " +
                    "soient adaptées à vous. Elles restent sur l'appareil et ne partent qu'avec vos " +
                    "messages, vers l'API Mammouth.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Utiliser mon profil")
                    Text(
                        "Désactivez pour discuter sans contexte personnel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { draft = draft.copy(enabled = it) }
                )
            }

            HorizontalDivider()

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Prénom / nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.role,
                onValueChange = { draft = draft.copy(role = it) },
                label = { Text("Métier / activité") },
                singleLine = true,
                supportingText = { Text("Ex. développeur Android, lycéen en terminale, kiné…") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.expertise,
                onValueChange = { draft = draft.copy(expertise = it) },
                label = { Text("Niveau et domaines") },
                supportingText = { Text("Ex. bon en Kotlin, débutant en design, parle anglais couramment.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.language,
                onValueChange = { draft = draft.copy(language = it) },
                label = { Text("Langue des réponses") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Ton des réponses", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UserProfile.TONES.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tone ->
                            FilterChip(
                                selected = draft.tone == tone,
                                onClick = {
                                    draft = draft.copy(tone = if (draft.tone == tone) "" else tone)
                                },
                                label = { Text(tone) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.about,
                onValueChange = { draft = draft.copy(about = it) },
                label = { Text("À propos de vous") },
                supportingText = { Text("Contexte utile : projets en cours, contraintes, préférences.") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.instructions,
                onValueChange = { draft = draft.copy(instructions = it) },
                label = { Text("Consignes permanentes") },
                supportingText = { Text("Ex. « Va droit au but », « cite tes sources », « pas de listes à puces ».") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Aperçu envoyé au modèle", style = MaterialTheme.typography.titleSmall)
            Card {
                Text(
                    text = draft.toSystemPrompt().ifBlank { "(profil vide ou désactivé)" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
