package com.mammouthclient.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mammouthclient.app.data.AppContainer
import com.mammouthclient.app.data.AppSettings
import com.mammouthclient.app.data.Assistant
import com.mammouthclient.app.data.AssistantStore
import com.mammouthclient.app.data.Attachment
import com.mammouthclient.app.data.AttachmentKind
import com.mammouthclient.app.data.ChatStore
import com.mammouthclient.app.data.Conversation
import com.mammouthclient.app.data.DefaultPrompts
import com.mammouthclient.app.data.Message
import com.mammouthclient.app.data.PromptStore
import com.mammouthclient.app.data.PromptTemplate
import com.mammouthclient.app.data.TokenUsage
import com.mammouthclient.app.net.ApiMessage
import com.mammouthclient.app.net.ChatEvent
import com.mammouthclient.app.net.MammouthApi
import com.mammouthclient.app.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Contenu d'une sauvegarde exportable. */
@Serializable
data class BackupPayload(
    val conversations: List<Conversation> = emptyList(),
    val assistants: List<Assistant> = emptyList(),
    val prompts: List<PromptTemplate> = emptyList()
)

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentId: String? = null,
    val availableModels: List<String> = MammouthApi.FALLBACK_MODELS,
    val modelsLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val assistants: List<Assistant> = emptyList(),
    val prompts: List<PromptTemplate> = emptyList(),
    val pendingAttachments: List<Attachment> = emptyList(),
    val importing: Boolean = false,
    val search: String = "",
    /** Texte poussé vers la zone de saisie depuis un autre écran (prompt, partage Android). */
    val stagedInput: String? = null,
    val showArchived: Boolean = false
) {
    val current: Conversation? get() = conversations.firstOrNull { it.id == currentId }
    val messages: List<Message> get() = current?.messages.orEmpty()

    /** Discussions filtrées par la recherche, épinglées d'abord. */
    val visibleConversations: List<Conversation>
        get() = conversations
            .filter { it.archived == showArchived }
            .filter { conversation ->
                search.isBlank() ||
                    conversation.title.contains(search, true) ||
                    conversation.messages.any { it.content.contains(search, true) }
            }
            .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })

    val archivedCount: Int get() = conversations.count { it.archived }

    fun assistantOf(conversation: Conversation?): Assistant? =
        assistants.firstOrNull { it.id == conversation?.assistantId }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = AppContainer.settings(application)
    private val api = AppContainer.api()
    private val store = ChatStore(application)
    private val assistantStore = AssistantStore(application)
    private val promptStore = PromptStore(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private var streamJob: Job? = null

    private val backupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    init {
        viewModelScope.launch {
            val conversations = store.load()
            val assistants = assistantStore.load()
            val saved = promptStore.load()
            _state.value = _state.value.copy(
                conversations = conversations,
                currentId = conversations.firstOrNull()?.id,
                assistants = assistants,
                prompts = DefaultPrompts.ALL + saved
            )
            if (conversations.isEmpty()) newConversation()
            if (settings.value.hasApiKey) refreshModels()
        }
    }

    /* ---------------- Discussions ---------------- */

    fun newConversation(assistantId: String? = null, initialText: String = "") {
        val assistant = _state.value.assistants.firstOrNull { it.id == assistantId }
        val conversation = Conversation(
            model = assistant?.model?.ifBlank { null } ?: settings.value.model,
            assistantId = assistantId
        )
        _state.value = _state.value.copy(
            conversations = listOf(conversation) + _state.value.conversations,
            currentId = conversation.id,
            pendingAttachments = emptyList(),
            error = null
        )
        persist()
        if (initialText.isNotBlank()) send(initialText)
    }

    fun selectConversation(id: String) {
        stop()
        _state.value = _state.value.copy(currentId = id, pendingAttachments = emptyList(), error = null)
    }

    fun deleteConversation(id: String) {
        stop()
        val remaining = _state.value.conversations.filterNot { it.id == id }
        _state.value = _state.value.copy(
            conversations = remaining,
            currentId = if (_state.value.currentId == id) remaining.firstOrNull()?.id else _state.value.currentId
        )
        if (remaining.isEmpty()) newConversation() else persist()
    }

    fun renameConversation(id: String, title: String) {
        if (title.isBlank()) return
        _state.value = _state.value.copy(
            conversations = _state.value.conversations.map {
                if (it.id == id) it.copy(title = title.trim()) else it
            }
        )
        persist()
    }

    fun togglePin(id: String) {
        _state.value = _state.value.copy(
            conversations = _state.value.conversations.map {
                if (it.id == id) it.copy(pinned = !it.pinned) else it
            }
        )
        persist()
    }

    fun toggleArchive(id: String) {
        _state.value = _state.value.copy(
            conversations = _state.value.conversations.map {
                if (it.id == id) it.copy(archived = !it.archived) else it
            }
        )
        val stillVisible = _state.value.visibleConversations.any { it.id == _state.value.currentId }
        if (!stillVisible) {
            _state.value = _state.value.copy(
                currentId = _state.value.visibleConversations.firstOrNull()?.id
            )
        }
        persist()
    }

    fun toggleShowArchived() {
        _state.value = _state.value.copy(showArchived = !_state.value.showArchived)
    }

    fun deleteAllConversations() {
        stop()
        viewModelScope.launch {
            store.clear()
            _state.value = _state.value.copy(conversations = emptyList(), currentId = null)
            newConversation()
        }
    }

    fun setSearch(query: String) {
        _state.value = _state.value.copy(search = query)
    }

    fun toggleWebSearch() {
        updateCurrent { it.copy(webSearch = !it.webSearch) }
        persist()
    }

    fun applyAssistant(assistantId: String?) {
        val assistant = _state.value.assistants.firstOrNull { it.id == assistantId }
        updateCurrent { conversation ->
            conversation.copy(
                assistantId = assistantId,
                model = assistant?.model?.ifBlank { null } ?: conversation.model
            )
        }
        persist()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearInfo() {
        _state.value = _state.value.copy(info = null)
    }

    fun notify(message: String) {
        _state.value = _state.value.copy(info = message)
    }

    /** Pousse du texte dans la zone de saisie (bibliothèque de prompts, partage Android). */
    fun stageInput(text: String) {
        if (text.isBlank()) return
        _state.value = _state.value.copy(stagedInput = text)
    }

    fun consumeStagedInput() {
        _state.value = _state.value.copy(stagedInput = null)
    }

    /* ---------------- Modèles ---------------- */

    fun refreshModels() {
        if (!settings.value.hasApiKey) return
        viewModelScope.launch {
            _state.value = _state.value.copy(modelsLoading = true)
            val result = runCatching { api.listModels(settings.value) }
            _state.value = _state.value.copy(
                modelsLoading = false,
                availableModels = result.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: _state.value.availableModels,
                error = result.exceptionOrNull()?.message ?: _state.value.error
            )
        }
    }

    fun selectModel(model: String) {
        settingsRepository.update { it.copy(model = model) }
        updateCurrent { it.copy(model = model) }
        persist()
    }

    fun toggleFavoriteModel(model: String) {
        settingsRepository.update { current ->
            val favorites = current.favoriteModels.toMutableSet()
            if (!favorites.add(model)) favorites.remove(model)
            current.copy(favoriteModels = favorites)
        }
    }

    /* ---------------- Pièces jointes ---------------- */

    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true)
            val imported = uris.flatMap { uri ->
                runCatching { FileUtils.importUri(appContext, uri) }.getOrDefault(emptyList())
            }
            _state.value = _state.value.copy(
                importing = false,
                pendingAttachments = _state.value.pendingAttachments + imported,
                error = if (imported.isEmpty()) "Fichier non pris en charge ou illisible." else _state.value.error
            )
        }
    }

    fun removeAttachment(id: String) {
        _state.value = _state.value.copy(
            pendingAttachments = _state.value.pendingAttachments.filterNot { it.id == id }
        )
    }

    /* ---------------- Envoi ---------------- */

    fun send(rawText: String) {
        val text = rawText.trim()
        val attachments = _state.value.pendingAttachments
        if ((text.isEmpty() && attachments.isEmpty()) || _state.value.isStreaming) return

        val snapshot = settings.value
        if (!snapshot.hasApiKey) {
            _state.value = _state.value.copy(error = "Ajoutez d'abord votre clé API Mammouth dans les réglages.")
            return
        }
        if (_state.value.current == null) newConversation()

        val userMessage = Message(
            role = Message.ROLE_USER,
            content = text,
            attachments = attachments
        )
        val placeholder = Message(role = Message.ROLE_ASSISTANT, content = "")
        updateCurrent { conversation ->
            conversation.copy(messages = conversation.messages + userMessage + placeholder)
                .withDerivedTitle()
        }
        _state.value = _state.value.copy(pendingAttachments = emptyList())

        launchGeneration(snapshot)
    }

    /** Relance la dernière réponse (bouton « Régénérer »). */
    fun regenerate() {
        if (_state.value.isStreaming) return
        val conversation = _state.value.current ?: return
        val messages = conversation.messages.toMutableList()
        val lastAssistant = messages.indexOfLast { it.role == Message.ROLE_ASSISTANT }
        if (lastAssistant >= 0) messages.removeAt(lastAssistant)
        messages += Message(role = Message.ROLE_ASSISTANT, content = "")
        updateCurrent { it.copy(messages = messages) }
        launchGeneration(settings.value)
    }

    /** Régénère la dernière réponse avec un autre modèle. */
    fun regenerateWith(model: String) {
        if (_state.value.isStreaming) return
        updateCurrent { it.copy(model = model, webSearch = false) }
        regenerate()
    }

    /** Demande au modèle de poursuivre une réponse tronquée. */
    fun continueResponse() {
        if (_state.value.isStreaming) return
        val conversation = _state.value.current ?: return
        if (conversation.messages.lastOrNull()?.role != Message.ROLE_ASSISTANT) return
        updateCurrent {
            it.copy(
                messages = it.messages +
                    Message(role = Message.ROLE_USER, content = "Continue exactement là où tu t'es arrêté, sans répéter.") +
                    Message(role = Message.ROLE_ASSISTANT, content = "")
            )
        }
        launchGeneration(settings.value)
    }

    /**
     * Envoie la même question à plusieurs modèles et empile les réponses dans la
     * discussion, chacune identifiée par son modèle.
     */
    fun compareModels(prompt: String, models: List<String>) {
        val text = prompt.trim()
        if (text.isEmpty() || models.isEmpty() || _state.value.isStreaming) return
        val snapshot = settings.value
        if (!snapshot.hasApiKey) {
            _state.value = _state.value.copy(error = "Ajoutez d'abord votre clé API Mammouth.")
            return
        }
        if (_state.value.current == null) newConversation()

        updateCurrent { conversation ->
            conversation.copy(
                messages = conversation.messages + Message(role = Message.ROLE_USER, content = text)
            ).withDerivedTitle()
        }

        val history = buildHistory(snapshot, _state.value.current ?: return)
        _state.value = _state.value.copy(isStreaming = true, error = null)

        streamJob = viewModelScope.launch {
            models.forEach { model ->
                updateCurrent {
                    it.copy(messages = it.messages + Message(role = Message.ROLE_ASSISTANT, content = "", model = model))
                }
                val builder = StringBuilder()
                api.streamChat(snapshot, history, model)
                    .catch { throwable ->
                        if (throwable is CancellationException) throw throwable
                        replaceLastAssistant(
                            "⚠️ ${throwable.message ?: "échec"}",
                            model = model,
                            isError = true
                        )
                    }
                    .collect { event ->
                        when (event) {
                            is ChatEvent.Delta -> {
                                builder.append(event.text)
                                replaceLastAssistant(builder.toString(), model = model)
                            }

                            is ChatEvent.Completed -> replaceLastAssistant(
                                builder.toString(),
                                model = model,
                                usage = event.usage?.let {
                                    TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens)
                                }
                            )
                        }
                    }
            }
            _state.value = _state.value.copy(isStreaming = false)
            persist()
        }
    }

    /** Modifie un message utilisateur et relance la conversation à partir de là. */
    fun editAndResend(messageId: String, newText: String) {
        if (_state.value.isStreaming || newText.isBlank()) return
        val conversation = _state.value.current ?: return
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val kept = conversation.messages.take(index).toMutableList()
        kept += conversation.messages[index].copy(content = newText.trim())
        kept += Message(role = Message.ROLE_ASSISTANT, content = "")
        updateCurrent { it.copy(messages = kept) }
        launchGeneration(settings.value)
    }

    fun deleteMessage(messageId: String) {
        updateCurrent { conversation ->
            conversation.copy(messages = conversation.messages.filterNot { it.id == messageId })
        }
        persist()
    }

    private fun launchGeneration(snapshot: AppSettings) {
        val conversation = _state.value.current ?: return
        val model = effectiveModel(conversation, snapshot)
        val history = buildHistory(snapshot, conversation)
        _state.value = _state.value.copy(isStreaming = true, error = null)

        streamJob = viewModelScope.launch {
            val builder = StringBuilder()
            var usage: TokenUsage? = null
            var failed = false

            api.streamChat(snapshot, history, model)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    failed = true
                    val message = throwable.message ?: "Échec de la requête."
                    val body = if (builder.isEmpty()) message else "$builder\n\n⚠️ $message"
                    replaceLastAssistant(body, model = model, isError = true)
                    _state.value = _state.value.copy(error = message)
                }
                .collect { event ->
                    when (event) {
                        is ChatEvent.Delta -> {
                            builder.append(event.text)
                            replaceLastAssistant(builder.toString(), model = model)
                        }

                        is ChatEvent.Completed -> {
                            usage = event.usage?.let {
                                TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens)
                            }
                            replaceLastAssistant(
                                builder.toString(),
                                model = event.model?.ifBlank { null } ?: model,
                                usage = usage
                            )
                        }
                    }
                }

            if (!failed && builder.isEmpty()) {
                replaceLastAssistant("(réponse vide)", model = model, isError = true)
            }
            _state.value = _state.value.copy(isStreaming = false)
            persist()
            if (!failed) maybeAutoTitle(snapshot)
        }
    }

    /** Fait nommer la discussion par le modèle après le premier échange. */
    private fun maybeAutoTitle(snapshot: AppSettings) {
        if (!snapshot.autoTitle || !snapshot.hasApiKey) return
        val conversation = _state.value.current ?: return
        if (conversation.messages.size != 2) return

        viewModelScope.launch {
            val question = conversation.messages.firstOrNull()?.content.orEmpty().take(600)
            val answer = conversation.messages.lastOrNull()?.content.orEmpty().take(600)
            val title = runCatching {
                api.completeOnce(
                    settings = snapshot,
                    messages = listOf(
                        ApiMessage.text(
                            Message.ROLE_USER,
                            "Donne un titre court (5 mots maximum), sans guillemets ni ponctuation " +
                                "finale, résumant cet échange :\n\nQ: $question\nR: $answer"
                        )
                    ),
                    model = snapshot.model,
                    maxTokens = 24
                )
            }.getOrNull()
                ?.trim()
                ?.trim('"', '«', '»', '.', ' ')
                ?.lineSequence()
                ?.firstOrNull()
                .orEmpty()

            if (title.isNotBlank() && title.length <= 60) {
                renameConversation(conversation.id, title)
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        if (_state.value.isStreaming) {
            _state.value = _state.value.copy(isStreaming = false)
            persist()
        }
    }

    /* ---------------- Projets (assistants) ---------------- */

    fun saveAssistant(assistant: Assistant) {
        val existing = _state.value.assistants.any { it.id == assistant.id }
        val updated = if (existing) {
            _state.value.assistants.map { if (it.id == assistant.id) assistant else it }
        } else {
            _state.value.assistants + assistant
        }
        _state.value = _state.value.copy(assistants = updated)
        viewModelScope.launch { assistantStore.save(updated) }
    }

    fun deleteAssistant(id: String) {
        val updated = _state.value.assistants.filterNot { it.id == id }
        _state.value = _state.value.copy(
            assistants = updated,
            conversations = _state.value.conversations.map {
                if (it.assistantId == id) it.copy(assistantId = null) else it
            }
        )
        viewModelScope.launch { assistantStore.save(updated) }
        persist()
    }

    /* ---------------- Bibliothèque de prompts ---------------- */

    fun savePrompt(prompt: PromptTemplate) {
        val custom = _state.value.prompts.filterNot { it.builtIn }
        val updated = if (custom.any { it.id == prompt.id }) {
            custom.map { if (it.id == prompt.id) prompt else it }
        } else {
            custom + prompt
        }
        _state.value = _state.value.copy(prompts = DefaultPrompts.ALL + updated)
        viewModelScope.launch { promptStore.save(updated) }
    }

    fun deletePrompt(id: String) {
        val custom = _state.value.prompts.filterNot { it.builtIn }.filterNot { it.id == id }
        _state.value = _state.value.copy(prompts = DefaultPrompts.ALL + custom)
        viewModelScope.launch { promptStore.save(custom) }
    }

    /* ---------------- Export ---------------- */

    /** Rend la discussion courante en Markdown, pour le partage. */
    fun exportCurrentAsMarkdown(): String {
        val conversation = _state.value.current ?: return ""
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        return buildString {
            appendLine("# ${conversation.title}")
            appendLine("_${conversation.model} — ${formatter.format(Date(conversation.updatedAt))}_")
            appendLine()
            conversation.messages.forEach { message ->
                val who = if (message.role == Message.ROLE_USER) "**Vous**" else "**${message.model.ifBlank { "Assistant" }}**"
                appendLine("$who :")
                appendLine()
                appendLine(message.content)
                if (message.attachments.isNotEmpty()) {
                    appendLine()
                    appendLine("_Pièces jointes : ${message.attachments.joinToString { it.name }}_")
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    /** Sauvegarde complète (discussions, projets, prompts) au format JSON. */
    fun exportBackup(): String = runCatching {
        backupJson.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                conversations = _state.value.conversations,
                assistants = _state.value.assistants,
                prompts = _state.value.prompts.filterNot { it.builtIn }
            )
        )
    }.getOrDefault("")

    /** Restaure une sauvegarde ; les éléments existants sont conservés. */
    fun importBackup(raw: String) {
        val payload = runCatching {
            backupJson.decodeFromString(BackupPayload.serializer(), raw)
        }.getOrNull()

        if (payload == null) {
            _state.value = _state.value.copy(error = "Fichier de sauvegarde illisible.")
            return
        }

        val existingIds = _state.value.conversations.map { it.id }.toSet()
        val mergedConversations = _state.value.conversations +
            payload.conversations.filterNot { it.id in existingIds }
        val assistantIds = _state.value.assistants.map { it.id }.toSet()
        val mergedAssistants = _state.value.assistants +
            payload.assistants.filterNot { it.id in assistantIds }
        val customPrompts = _state.value.prompts.filterNot { it.builtIn }
        val promptIds = customPrompts.map { it.id }.toSet()
        val mergedPrompts = customPrompts + payload.prompts.filterNot { it.id in promptIds }

        _state.value = _state.value.copy(
            conversations = mergedConversations,
            assistants = mergedAssistants,
            prompts = DefaultPrompts.ALL + mergedPrompts,
            currentId = _state.value.currentId ?: mergedConversations.firstOrNull()?.id,
            info = "Sauvegarde restaurée : ${payload.conversations.size} discussion(s)."
        )
        persist()
        viewModelScope.launch {
            assistantStore.save(mergedAssistants)
            promptStore.save(mergedPrompts)
        }
    }

    /* ---------------- Helpers ---------------- */

    private fun effectiveModel(conversation: Conversation, snapshot: AppSettings): String = when {
        conversation.webSearch && snapshot.searchModel.isNotBlank() -> snapshot.searchModel
        conversation.model.isNotBlank() -> conversation.model
        else -> snapshot.model
    }

    private fun buildHistory(snapshot: AppSettings, conversation: Conversation): List<ApiMessage> {
        val assistant = _state.value.assistantOf(conversation)
        val systemParts = mutableListOf<String>()
        if (snapshot.systemPrompt.isNotBlank()) systemParts += snapshot.systemPrompt
        assistant?.instructions?.takeIf { it.isNotBlank() }?.let { systemParts += it }
        assistant?.documents?.filter { it.kind == AttachmentKind.TEXT }?.forEach { document ->
            systemParts += "Document de référence « ${document.name} » :\n${document.text.take(40_000)}"
        }

        val history = mutableListOf<ApiMessage>()
        if (systemParts.isNotEmpty()) {
            history += ApiMessage.text(Message.ROLE_SYSTEM, systemParts.joinToString("\n\n"))
        }

        conversation.messages
            .filterNot { it.role == Message.ROLE_ASSISTANT && it.content.isBlank() }
            .filterNot { it.isError }
            .forEach { message ->
                val textParts = mutableListOf<String>()
                if (message.content.isNotBlank()) textParts += message.content
                message.attachments.filter { it.kind == AttachmentKind.TEXT }.forEach { document ->
                    textParts += "Fichier joint « ${document.name} » :\n${document.text.take(40_000)}"
                }
                val body = textParts.joinToString("\n\n")

                val images = message.attachments
                    .filter { it.kind == AttachmentKind.IMAGE && it.path.isNotBlank() }
                    .mapNotNull { FileUtils.toBase64DataUrl(it.path) }

                history += if (images.isEmpty()) {
                    ApiMessage.text(message.role, body)
                } else {
                    ApiMessage.multimodal(message.role, body, images)
                }
            }

        return history
    }

    private fun replaceLastAssistant(
        content: String,
        model: String = "",
        isError: Boolean = false,
        usage: TokenUsage? = null
    ) {
        updateCurrent { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == Message.ROLE_ASSISTANT }
            if (index >= 0) {
                messages[index] = messages[index].copy(
                    content = content,
                    isError = isError,
                    model = model.ifBlank { messages[index].model },
                    usage = usage ?: messages[index].usage
                )
            } else {
                messages += Message(
                    role = Message.ROLE_ASSISTANT,
                    content = content,
                    isError = isError,
                    model = model,
                    usage = usage
                )
            }
            conversation.copy(messages = messages)
        }
    }

    private fun updateCurrent(transform: (Conversation) -> Conversation) {
        val id = _state.value.currentId ?: return
        _state.value = _state.value.copy(
            conversations = _state.value.conversations.map { conversation ->
                if (conversation.id == id) {
                    transform(conversation).copy(updatedAt = System.currentTimeMillis())
                } else {
                    conversation
                }
            }
        )
    }

    private fun persist() {
        val snapshot = _state.value.conversations
        viewModelScope.launch { store.save(snapshot) }
    }
}
