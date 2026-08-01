package io.github.banannanannanane.mammouth.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.banannanannanane.mammouth.R
import io.github.banannanannanane.mammouth.data.ChatMessage
import io.github.banannanannanane.mammouth.data.Conversation
import io.github.banannanannanane.mammouth.data.ConversationStore
import io.github.banannanannanane.mammouth.data.ModelInfo
import io.github.banannanannanane.mammouth.data.Role
import io.github.banannanannanane.mammouth.data.Settings
import io.github.banannanannanane.mammouth.data.SettingsRepository
import io.github.banannanannanane.mammouth.net.ApiException
import io.github.banannanannanane.mammouth.net.MammouthApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** The assistant turn currently being generated, kept out of storage until it completes. */
data class StreamingTurn(
    val conversationId: String,
    val text: String = "",
    val model: String,
)

data class ChatUiState(
    val currentConversationId: String? = null,
    val input: String = "",
    val models: List<ModelInfo> = emptyList(),
    val modelsLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val connectionTestRunning: Boolean = false,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val store = ConversationStore(application, viewModelScope)
    private val api = MammouthApi()

    val settings: StateFlow<Settings> = settingsRepository.settings
    val conversations: StateFlow<List<Conversation>> = store.conversations

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _streaming = MutableStateFlow<StreamingTurn?>(null)
    val streaming: StateFlow<StreamingTurn?> = _streaming.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            store.load()
            _uiState.update { it.copy(currentConversationId = store.conversations.value.firstOrNull()?.id) }
            refreshModels()
        }
    }

    // ---------------------------------------------------------------- chat

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value) }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _streaming.value != null) return

        if (!settings.value.hasApiKey) {
            _uiState.update { it.copy(error = string(R.string.error_no_api_key)) }
            return
        }

        val existing = store.get(_uiState.value.currentConversationId)
        val base = existing ?: Conversation(model = settings.value.model)
        val userMessage = ChatMessage(role = Role.USER, content = text)
        val updated = base.copy(
            title = base.title.ifBlank { deriveTitle(text) },
            model = base.model ?: settings.value.model,
            messages = base.messages + userMessage,
        )

        store.upsert(updated)
        _uiState.update { it.copy(currentConversationId = updated.id, input = "", error = null) }
        generate(updated.id)
    }

    /** Re-runs the last user turn, discarding the assistant answer that followed it. */
    fun regenerate() {
        if (_streaming.value != null) return
        val conversation = store.get(_uiState.value.currentConversationId) ?: return
        val lastUserIndex = conversation.messages.indexOfLast { it.role == Role.USER }
        if (lastUserIndex < 0) return

        val trimmed = conversation.copy(messages = conversation.messages.take(lastUserIndex + 1))
        store.upsert(trimmed)
        generate(trimmed.id)
    }

    fun stop() {
        streamJob?.cancel()
    }

    private fun generate(conversationId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val conversation = store.get(conversationId) ?: return@launch
            val model = conversation.model ?: settings.value.model
            _streaming.value = StreamingTurn(conversationId = conversationId, model = model)

            var failure: String? = null
            try {
                api.chat(
                    settings = settings.value,
                    messages = conversation.messages,
                    model = model,
                ) { delta ->
                    _streaming.update { current -> current?.copy(text = current.text + delta) }
                }
            } catch (cancellation: CancellationException) {
                commitStreamedTurn(conversationId, model, null)
                throw cancellation
            } catch (throwable: Throwable) {
                failure = describe(throwable)
            }
            commitStreamedTurn(conversationId, model, failure)
        }
    }

    /** Moves the streamed text (partial or complete) into the stored conversation. */
    private fun commitStreamedTurn(conversationId: String, model: String, failure: String?) {
        val turn = _streaming.value ?: return
        _streaming.value = null

        val conversation = store.get(conversationId) ?: return
        if (turn.text.isNotBlank() || failure != null) {
            store.upsert(
                conversation.copy(
                    messages = conversation.messages + ChatMessage(
                        role = Role.ASSISTANT,
                        content = turn.text,
                        model = model,
                        error = failure,
                    ),
                ),
            )
        }
        if (failure != null) _uiState.update { it.copy(error = failure) }
    }

    // -------------------------------------------------------- conversations

    fun newConversation() {
        stop()
        _uiState.update { it.copy(currentConversationId = null, input = "", error = null) }
    }

    fun selectConversation(id: String) {
        stop()
        _uiState.update { it.copy(currentConversationId = id, error = null) }
    }

    fun deleteConversation(id: String) {
        if (_streaming.value?.conversationId == id) stop()
        store.delete(id)
        if (_uiState.value.currentConversationId == id) {
            _uiState.update { it.copy(currentConversationId = store.conversations.value.firstOrNull()?.id) }
        }
    }

    fun renameConversation(id: String, title: String) {
        val conversation = store.get(id) ?: return
        store.upsert(conversation.copy(title = title.trim().ifBlank { conversation.title }))
    }

    fun deleteAllConversations() {
        stop()
        store.deleteAll()
        _uiState.update { it.copy(currentConversationId = null) }
    }

    fun currentConversation(): Conversation? = store.get(_uiState.value.currentConversationId)

    // ------------------------------------------------------------ settings

    fun setApiKey(value: String) = settingsRepository.setApiKey(value)

    fun setBaseUrl(value: String) = settingsRepository.setBaseUrl(value)

    fun setSystemPrompt(value: String) = settingsRepository.setSystemPrompt(value)

    fun setTemperature(value: Float) = settingsRepository.setTemperature(value)

    fun setStreaming(value: Boolean) = settingsRepository.setStreaming(value)

    /** Applies a model globally and to the open conversation. */
    fun selectModel(modelId: String) {
        settingsRepository.setModel(modelId)
        store.get(_uiState.value.currentConversationId)?.let { store.upsert(it.copy(model = modelId)) }
    }

    fun refreshModels() {
        if (_uiState.value.modelsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(modelsLoading = true) }
            val models = runCatching { api.listModels(settings.value) }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    modelsLoading = false,
                    models = models.ifEmpty { MammouthApi.FALLBACK_MODELS },
                    error = if (models.isEmpty()) string(R.string.error_models) else it.error,
                )
            }
        }
    }

    fun testConnection() {
        if (_uiState.value.connectionTestRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(connectionTestRunning = true, error = null) }
            val result = runCatching { api.checkCredentials(settings.value, settings.value.model) }
            _uiState.update {
                it.copy(
                    connectionTestRunning = false,
                    notice = result.exceptionOrNull()?.let { error -> describe(error) }
                        ?: string(R.string.connection_ok),
                )
            }
        }
    }

    fun consumeNotice() = _uiState.update { it.copy(notice = null) }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    // ------------------------------------------------------------- helpers

    private fun describe(throwable: Throwable): String = when (throwable) {
        is ApiException -> when (throwable.statusCode) {
            401, 403 -> string(R.string.error_unauthorized)
            429 -> string(R.string.error_rate_limited)
            else -> throwable.apiMessage?.takeIf { it.isNotBlank() }
                ?: "HTTP ${throwable.statusCode}"
        }
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            string(R.string.error_network)
        is IOException -> throwable.message ?: string(R.string.error_network)
        else -> throwable.message ?: throwable::class.java.simpleName
    }

    private fun deriveTitle(firstMessage: String): String =
        firstMessage.lineSequence().firstOrNull()?.trim().orEmpty()
            .take(48)
            .ifBlank { string(R.string.new_chat) }

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)
}
