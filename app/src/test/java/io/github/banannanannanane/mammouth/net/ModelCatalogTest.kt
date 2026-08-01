package io.github.banannanannanane.mammouth.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    private fun parse(raw: String) = ModelCatalog.parse(Json.parseToJsonElement(raw))

    @Test
    fun `reads the OpenAI-style envelope`() {
        val models = parse(
            """
            {"object":"list","data":[
              {"id":"gpt-4.1","object":"model","owned_by":"openai"},
              {"id":"claude-sonnet-4-6","object":"model","owned_by":"anthropic"}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("claude-sonnet-4-6", "gpt-4.1"), models.map { it.id })
        assertEquals("openai", models.first { it.id == "gpt-4.1" }.provider)
    }

    @Test
    fun `reads a bare array with display names and context windows`() {
        val models = parse(
            """
            [
              {"id":"glm-5.2","name":"GLM 5.2","context_window":128000,"description":"fast"},
              "minimax-m3"
            ]
            """.trimIndent(),
        )

        val glm = models.first { it.id == "glm-5.2" }
        assertEquals("GLM 5.2", glm.name)
        assertEquals(128000, glm.contextWindow!!)

        val minimax = models.first { it.id == "minimax-m3" }
        assertEquals("minimax-m3", minimax.name)
        assertNull(minimax.contextWindow)
    }

    @Test
    fun `infers the provider from a namespaced id`() {
        val models = parse("""[{"id":"google/gemini-2.5-flash"}]""")
        assertEquals("google", models.single().provider)
    }

    @Test
    fun `ignores unusable entries and duplicates`() {
        val models = parse(
            """
            {"models":[
              {"id":"gpt-4.1"},
              {"id":"gpt-4.1"},
              {"unexpected":"shape"},
              42
            ]}
            """.trimIndent(),
        )

        assertEquals(1, models.size)
        assertEquals("gpt-4.1", models.single().id)
    }

    @Test
    fun `survives an unexpected payload`() {
        assertTrue(parse("""{"error":"nope"}""").isEmpty())
        assertTrue(parse("""17""").isEmpty())
    }
}
