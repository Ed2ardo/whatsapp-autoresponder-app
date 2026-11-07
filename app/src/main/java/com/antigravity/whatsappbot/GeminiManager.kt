package com.antigravity.whatsappbot

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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateReply(contactName: String, userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            throw IllegalStateException("API Key de Gemini no configurada.")
        }

        val model = prefs.geminiModel.ifBlank { "gemini-3.5-flash-lite" }
        val systemPrompt = prefs.systemPrompt

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        // Build Payload with Conversation History (Context Memory)
        val payload = JsonObject()

        // System Instruction
        if (systemPrompt.isNotBlank()) {
            val systemObj = JsonObject()
            val sysParts = JsonArray()
            val sysPartText = JsonObject().apply { addProperty("text", systemPrompt) }
            sysParts.add(sysPartText)
            systemObj.add("parts", sysParts)
            payload.add("systemInstruction", systemObj)
        }

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

        // Add current incoming message
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
            addProperty("maxOutputTokens", 160)
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
}
