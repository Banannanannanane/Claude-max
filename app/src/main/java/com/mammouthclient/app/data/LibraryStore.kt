package com.mammouthclient.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Persistance JSON générique (projets, prompts). */
open class JsonListStore<T>(
    context: Context,
    fileName: String,
    private val itemSerializer: KSerializer<T>
) {
    private val file = File(context.applicationContext.filesDir, fileName)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer get() = ListSerializer(itemSerializer)

    suspend fun load(): List<T> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    suspend fun save(items: List<T>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(serializer, items))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
        Unit
    }
}

class AssistantStore(context: Context) :
    JsonListStore<Assistant>(context, "assistants.json", Assistant.serializer())

class PromptStore(context: Context) :
    JsonListStore<PromptTemplate>(context, "prompts.json", PromptTemplate.serializer())
