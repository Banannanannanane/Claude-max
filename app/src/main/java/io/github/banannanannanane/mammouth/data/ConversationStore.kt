package io.github.banannanannanane.mammouth.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Conversation history, persisted as a single JSON file in the app's private
 * storage. Nothing leaves the device except the messages sent to the API.
 */
class ConversationStore(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writeMutex = Mutex()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    suspend fun load() {
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching { json.decodeFromString<List<Conversation>>(file.readText()) }
                .getOrDefault(emptyList())
        }
        _conversations.value = loaded.sortedByDescending { it.updatedAt }
    }

    fun upsert(conversation: Conversation) {
        val updated = conversation.copy(updatedAt = System.currentTimeMillis())
        val current = _conversations.value.toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index >= 0) current[index] = updated else current.add(0, updated)
        _conversations.value = current.sortedByDescending { it.updatedAt }
        persist()
    }

    fun delete(id: String) {
        _conversations.value = _conversations.value.filterNot { it.id == id }
        persist()
    }

    fun deleteAll() {
        _conversations.value = emptyList()
        persist()
    }

    fun get(id: String?): Conversation? = _conversations.value.firstOrNull { it.id == id }

    private fun persist() {
        val snapshot = _conversations.value
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                runCatching {
                    val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                    tmp.writeText(json.encodeToString(snapshot))
                    if (!tmp.renameTo(file)) {
                        file.writeText(tmp.readText())
                        tmp.delete()
                    }
                }
            }
        }
    }

    private companion object {
        const val FILE_NAME = "conversations.json"
    }
}
