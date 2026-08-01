package com.mammouthclient.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Persistance locale des conversations dans un simple fichier JSON. */
class ChatStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "conversations.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Conversation.serializer())

    suspend fun load(): List<Conversation> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }

    suspend fun save(conversations: List<Conversation>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(serializer, conversations))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        Unit
    }
}
