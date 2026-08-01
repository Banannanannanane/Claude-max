package com.mammouthclient.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mammouthclient.app.data.AppContainer
import com.mammouthclient.app.data.AppSettings
import com.mammouthclient.app.data.ChatStore
import com.mammouthclient.app.data.Conversation
import com.mammouthclient.app.data.Message
import com.mammouthclient.app.net.ApiMessage
import com.mammouthclient.app.net.MammouthApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentId: String? = null,
    val availableModels: List<String> = MammouthApi.FALLBACK_MODELS,
    val modelsLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null
) {
    val current: Conversation? get() = conversations.firstOrNull { it.id == currentId }
    val messages: List<Message> get() = current?.messages.orEmpty()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = AppContainer.settings(application)
    private val store = ChatStore(application)
    private val api = MammouthApi()

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = store.load()
            _state.value = _state.value.copy(
                conversations = loaded,
                currentId = loaded.firstOrNull()?.id
            )
            if (loaded.isEmpty()) newConversation()
            if (settings.value.hasApiKey) refreshModels()
        }
    }

    /* ---------------- Conversations ---------------- */

    fun newConversation() {
        val conversation = Conversation(model = settings.value.model)
        _state.value = _state.value.copy(
            conversations = listOf(conversation) + _state.value.conversations,
            currentId = conversation.id,
            error = null
        )
        persist()
    }

    fun selectConversation(id: String) {
        stop()
        _state.value = _state.value.copy(currentId = id, error = null)
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

    fun deleteAllConversations() {
        stop()
        viewModelScope.launch {
            store.clear()
            _state.value = _state.value.copy(conversations = emptyList(), currentId = null)
            newConversation()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /* ---------------- Modèles ---------------- */

    fun refreshModels() {
        if (!settings.value.hasApiKey) return
        viewModelScope.launch {
            _state.value = _state.value.copy(modelsLoading = true)
            val result = runCatching { api.listModels(settings.value) }
            _state.value = _state.value.copy(
                modelsLoading = false,
                availableModels = result.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: _state.value.availableModels
            )
        }
    }

    fun selectModel(model: String) {
        settingsRepository.update { it.copy(model = model) }
        updateCurrent { it.copy(model = model) }
    }

    /* ---------------- Envoi ---------------- */

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _state.value.isStreaming) return

        val settingsSnapshot = settings.value
        if (!settingsSnapshot.hasApiKey) {
            _state.value = _state.value.copy(error = "Ajoutez d'abord votre clé API Mammouth dans les réglages.")
            return
        }
        if (_state.value.current == null) newConversation()

        val userMessage = Message(role = Message.ROLE_USER, content = text)
        val placeholder = Message(role = Message.ROLE_ASSISTANT, content = "")
        updateCurrent { conversation ->
            conversation.copy(messages = conversation.messages + userMessage + placeholder)
                .withDerivedTitle()
        }

        val history = buildHistory(settingsSnapshot)
        _state.value = _state.value.copy(isStreaming = true, error = null)

        streamJob = viewModelScope.launch {
            val builder = StringBuilder()
            api.streamChat(settingsSnapshot, history)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val message = throwable.message ?: "Échec de la requête."
                    if (builder.isEmpty()) {
                        replaceLastAssistant(message, isError = true)
                    } else {
                        replaceLastAssistant("$builder\n\n⚠️ $message", isError = true)
                    }
                    _state.value = _state.value.copy(error = message)
                }
                .collect { delta ->
                    builder.append(delta)
                    replaceLastAssistant(builder.toString())
                }

            if (builder.isEmpty() && _state.value.error == null) {
                replaceLastAssistant("(réponse vide)", isError = true)
            }
            _state.value = _state.value.copy(isStreaming = false)
            persist()
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

    /** Relance la dernière question de l'utilisateur (utile après une erreur réseau). */
    fun retryLast() {
        val conversation = _state.value.current ?: return
        val lastUser = conversation.messages.lastOrNull { it.role == Message.ROLE_USER } ?: return
        val trimmed = conversation.messages.subList(
            0,
            conversation.messages.indexOf(lastUser)
        )
        updateCurrent { it.copy(messages = trimmed) }
        send(lastUser.content)
    }

    /* ---------------- Helpers ---------------- */

    private fun buildHistory(settingsSnapshot: AppSettings): List<ApiMessage> {
        val conversation = _state.value.current ?: return emptyList()
        val body = conversation.messages
            .filterNot { it.role == Message.ROLE_ASSISTANT && it.content.isBlank() }
            .filterNot { it.isError }
            .map { ApiMessage(role = it.role, content = it.content) }

        return if (settingsSnapshot.systemPrompt.isNotBlank()) {
            listOf(ApiMessage(Message.ROLE_SYSTEM, settingsSnapshot.systemPrompt)) + body
        } else {
            body
        }
    }

    private fun replaceLastAssistant(content: String, isError: Boolean = false) {
        updateCurrent { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == Message.ROLE_ASSISTANT }
            if (index >= 0) {
                messages[index] = messages[index].copy(content = content, isError = isError)
            } else {
                messages += Message(role = Message.ROLE_ASSISTANT, content = content, isError = isError)
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
