package com.antigravity.whatsappbot

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiManager(private val prefs: AppPreferences) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "GeminiManager"
    }

    /**
     * Generates a realistic WhatsApp reply to incoming contacts, incorporating:
     * - Base System Prompt (Personality, Tone, Rules)
     * - Active Day Context & WhatsApp Statuses (What Eduardo uploaded or is currently doing)
     * - Dynamic Custom Rules (Key Q&A facts, prices, availability)
     * - Multi-turn conversation history
     */
    suspend fun generateReply(contactName: String, userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            throw IllegalStateException("API Key de Gemini no configurada.")
        }

        val model = prefs.geminiModel.ifBlank { "gemini-3.5-flash-lite" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        // Build Enriched System Instruction
        val enrichedSystemPrompt = buildString {
            append(prefs.systemPrompt)

            // Inject Active Day Context & WhatsApp Statuses
            val activeStatus = prefs.currentStatusContext
            if (activeStatus.isNotBlank()) {
                append("\n\n=== ESTADOS RECIENTES Y ACTIVIDAD DE HOY DE EDUARDO ===\n")
                append(activeStatus)
                append("\nINSTRUCCIÓN CRÍTICA: Si el contacto te habla sobre tus estados o historias de WhatsApp (o reacciona con emojis como 🔥, 🤤, 😂, etc.), responde con total naturalidad en primera persona basándote en esta actividad o estado reciente.")
            }

            // Inject Custom FAQ / Dynamic Rules
            val customRules = prefs.getCustomRules().filter { it.enabled }
            if (customRules.isNotEmpty()) {
                append("\n\n=== REGLAS OFICIALES Y DATOS CLAVE (PREGUNTAS Y RESPUESTAS) ===\n")
                append("Si el mensaje del contacto se relaciona con alguno de estos temas, responde fielmente según estas especificaciones:\n")
                for (rule in customRules) {
                    append("- Tema '${rule.trigger}': ${rule.response}\n")
                }
            }
        }

        val payload = JsonObject()

        // System Instruction
        val systemObj = JsonObject()
        val sysParts = JsonArray()
        sysParts.add(JsonObject().apply { addProperty("text", enrichedSystemPrompt) })
        systemObj.add("parts", sysParts)
        payload.add("systemInstruction", systemObj)

        // Contents (Multi-turn conversation history)
        val contentsArray = JsonArray()
        val history = ConversationMemory.getHistory(contactName)

        for (msg in history) {
            val contentItem = JsonObject().apply {
                addProperty("role", if (msg.role == "user") "user" else "model")
                val parts = JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", msg.text) })
                }
                add("parts", parts)
            }
            contentsArray.add(contentItem)
        }

        // Current incoming message
        val currentContent = JsonObject().apply {
            addProperty("role", "user")
            val parts = JsonArray().apply {
                add(JsonObject().apply { addProperty("text", userMessage) })
            }
            add("parts", parts)
        }
        contentsArray.add(currentContent)

        payload.add("contents", contentsArray)

        // Generation Config
        val genConfig = JsonObject().apply {
            addProperty("maxOutputTokens", 250)
            addProperty("temperature", 0.7)
        }
        payload.add("generationConfig", genConfig)

        val requestBody = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw RuntimeException("Gemini HTTP ${response.code}: $responseBody")
        }

        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
        val candidates = jsonResponse.getAsJsonArray("candidates")
        if (candidates != null && candidates.size() > 0) {
            val firstCandidate = candidates.get(0).asJsonObject
            val content = firstCandidate.getAsJsonObject("content")
            val parts = content.getAsJsonArray("parts")
            if (parts != null && parts.size() > 0) {
                val replyText = parts.get(0).asJsonObject.get("text").asString.trim()

                // Save to context memory
                ConversationMemory.addMessage(contactName, "user", userMessage)
                ConversationMemory.addMessage(contactName, "model", replyText)

                return@withContext replyText
            }
        }

        throw RuntimeException("Respuesta vacía de Gemini: $responseBody")
    }

    /**
     * Interacts with the user inside the app as their personal AI Copilot.
     * Takes Eduardo's natural language update (e.g. "Subí foto en Toro Burger", "Si preguntan por la moto vale 5M"),
     * generates a conversational confirmation, extracts/updates the daily status context,
     * and creates dynamic Q&A rules if detected.
     */
    suspend fun processCopilotInstruction(instruction: String): CopilotResponse = withContext(Dispatchers.IO) {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            return@withContext CopilotResponse(
                replyToUser = "⚠️ API Key de Gemini no configurada. Ve a la pestaña 'Ajustes' e ingrésala para que pueda procesar tus instrucciones con IA.",
                updatedStatusSummary = instruction
            )
        }

        val model = prefs.geminiModel.ifBlank { "gemini-3.5-flash-lite" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val currentStatus = prefs.currentStatusContext
        val currentRulesSummary = prefs.getCustomRules().filter { it.enabled }.joinToString("; ") { "${it.trigger} -> ${it.response}" }

        val copilotSystemPrompt = """
Eres el Copiloto de IA personal de Eduardo para su aplicación de auto-respuesta en WhatsApp.
Tu objetivo es ayudar a Eduardo a gestionar su contexto del día, estados que acaba de subir a WhatsApp, o reglas de respuesta personalizadas.

CONTEXTO / ESTADO PREVIO DE EDUARDO:
"${currentStatus.ifBlank { "(Ninguno registrado hoy)" }}"

REGLAS ACTIVAS EXISTENTES:
"${currentRulesSummary.ifBlank { "(Ninguna)" }}"

INSTRUCCIONES DE RESPUESTA:
Debes responder SIEMPRE con un objeto JSON válido (sin markdown adicional, solo el JSON) con la siguiente estructura:
{
  "replyToUser": "Mensaje en tono colombiano natural, cercano y breve (máximo 2 oraciones) confirmándole a Eduardo lo que entendiste y cómo responderás si la gente le escribe o reacciona a su estado",
  "updatedStatusSummary": "Resumen actualizado y conciso de lo que Eduardo está haciendo hoy o de sus estados recientes en WhatsApp. Si el mensaje no cambia el estado, mantén el resumen anterior. Si Eduardo dice que ya no está haciendo eso o que lo quite, límpialo",
  "newCustomRule": {
    "trigger": "palabras clave separadas por | (ej: moto|precio moto|la moto)",
    "response": "respuesta oficial directa que debe darse si preguntan eso"
  }
}
NOTA IMPORTANTE: Si el mensaje de Eduardo NO contiene una regla específica de tipo 'si preguntan X responder Y', el campo 'newCustomRule' DEBE SER null.
""".trimIndent()

        val payload = JsonObject()

        // System Instruction
        val systemObj = JsonObject()
        val sysParts = JsonArray()
        sysParts.add(JsonObject().apply { addProperty("text", copilotSystemPrompt) })
        systemObj.add("parts", sysParts)
        payload.add("systemInstruction", systemObj)

        // Contents
        val contentsArray = JsonArray()
        val contentItem = JsonObject().apply {
            addProperty("role", "user")
            val parts = JsonArray().apply {
                add(JsonObject().apply { addProperty("text", instruction) })
            }
            add("parts", parts)
        }
        contentsArray.add(contentItem)
        payload.add("contents", contentsArray)

        // Generation Config
        val genConfig = JsonObject().apply {
            addProperty("maxOutputTokens", 300)
            addProperty("temperature", 0.4)
        }
        payload.add("generationConfig", genConfig)

        val requestBody = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Copilot Error ${response.code}: $responseBody")
                // Graceful fallback: Save directly to status context
                prefs.currentStatusContext = instruction
                return@withContext CopilotResponse(
                    replyToUser = "✅ Anotado localmente: \"$instruction\". Ya quedó registrado en tu estado de hoy.",
                    updatedStatusSummary = instruction
                )
            }

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = jsonResponse.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val candidate = candidates.get(0).asJsonObject
                val parts = candidate.getAsJsonObject("content").getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    val rawText = parts.get(0).asJsonObject.get("text").asString.trim()
                    return@withContext parseCopilotJson(rawText, instruction)
                }
            }

            // Fallback
            prefs.currentStatusContext = instruction
            return@withContext CopilotResponse(
                replyToUser = "✅ Entendido. He actualizado tu estado actual con: \"$instruction\".",
                updatedStatusSummary = instruction
            )

        } catch (e: Exception) {
            Log.e(TAG, "Excepción en Copilot: ${e.message}", e)
            prefs.currentStatusContext = instruction
            return@withContext CopilotResponse(
                replyToUser = "✅ Guardado localmente en tu estado de hoy: \"$instruction\".",
                updatedStatusSummary = instruction
            )
        }
    }

    private fun parseCopilotJson(rawText: String, originalInstruction: String): CopilotResponse {
        var cleanJson = rawText.trim()
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.removePrefix("```json").trim()
        }
        if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.removePrefix("```").trim()
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.removeSuffix("```").trim()
        }

        return try {
            val parsedObj = gson.fromJson(cleanJson, JsonObject::class.java)
            val replyToUser = parsedObj.get("replyToUser")?.asString
                ?: "¡Listo Eduardo! Ya lo tengo presente para responder en WhatsApp."

            val updatedStatus = if (parsedObj.has("updatedStatusSummary") && !parsedObj.get("updatedStatusSummary").isJsonNull) {
                parsedObj.get("updatedStatusSummary").asString.trim()
            } else null

            if (!updatedStatus.isNullOrBlank()) {
                prefs.currentStatusContext = updatedStatus
            }

            var newRule: CustomRule? = null
            if (parsedObj.has("newCustomRule") && !parsedObj.get("newCustomRule").isJsonNull) {
                val ruleObj = parsedObj.getAsJsonObject("newCustomRule")
                val trigger = ruleObj.get("trigger")?.asString?.trim() ?: ""
                val resp = ruleObj.get("response")?.asString?.trim() ?: ""
                if (trigger.isNotBlank() && resp.isNotBlank()) {
                    newRule = CustomRule(trigger = trigger, response = resp, enabled = true)
                    prefs.addOrUpdateCustomRule(newRule)
                }
            }

            CopilotResponse(
                replyToUser = replyToUser,
                updatedStatusSummary = updatedStatus,
                newCustomRule = newRule
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo parsear JSON del Copiloto, usando texto plano: $rawText")
            prefs.currentStatusContext = originalInstruction
            CopilotResponse(
                replyToUser = rawText.ifBlank { "¡Listo Eduardo! Ya registré tu instrucción." },
                updatedStatusSummary = originalInstruction
            )
        }
    }
}
