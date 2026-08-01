package io.github.banannanannanane.mammouth.net

import io.github.banannanannanane.mammouth.data.ModelInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads the model catalogue defensively: the public endpoint and the
 * OpenAI-style `/models` endpoint do not share a shape, and either may grow new
 * fields, so anything unrecognised is ignored rather than fatal.
 */
internal object ModelCatalog {

    fun parse(root: JsonElement): List<ModelInfo> {
        val items: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> listOf("data", "models", "results")
                .firstNotNullOfOrNull { root[it] as? JsonArray }
                ?: root.values.filterIsInstance<JsonArray>().firstOrNull()
                ?: emptyList()
            else -> emptyList()
        }

        return items.mapNotNull { toModelInfo(it) }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }

    private fun toModelInfo(item: JsonElement): ModelInfo? = when (item) {
        is JsonPrimitive -> item.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { ModelInfo(id = it, name = it) }

        is JsonObject -> {
            val id = item.string("id") ?: item.string("model") ?: item.string("slug")
                ?: item.string("name")
            id?.let {
                ModelInfo(
                    id = it,
                    name = item.string("name") ?: item.string("display_name")
                        ?: item.string("displayName") ?: it,
                    provider = item.string("provider") ?: item.string("owned_by")
                        ?: item.string("company")
                        ?: it.substringBefore('/', missingDelimiterValue = "").ifBlank { null },
                    description = item.string("description"),
                    contextWindow = item.int("context_window") ?: item.int("context_length")
                        ?: item.int("contextWindow") ?: item.int("max_context"),
                )
            }
        }

        else -> null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let {
        it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.contentOrNull?.toIntOrNull()
    }
}
