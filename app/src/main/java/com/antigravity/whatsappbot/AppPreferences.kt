package com.antigravity.whatsappbot

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wa_bot_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var isBotEnabled: Boolean
        get() = prefs.getBoolean("bot_enabled", true)
        set(value) = prefs.edit().putBoolean("bot_enabled", value).apply()

    var isAiMode: Boolean
        get() = prefs.getBoolean("ai_mode", true)
        set(value) = prefs.edit().putBoolean("ai_mode", value).apply()

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value.trim()).apply()

    var geminiModel: String
        get() = prefs.getString("gemini_model", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite"
        set(value) = prefs.edit().putString("gemini_model", value).apply()

    var systemPrompt: String
        get() = prefs.getString("system_prompt",
            "Eres Eduardo respondiendo en tu propio WhatsApp personal. Tono: natural, relajado, directo y casual (máximo 1 o 2 oraciones cortas, como un chat real de WhatsApp). Si te preguntan qué haces, di que andas descansando con pereza o en el computador. REGLAS ESTRICTAS: 1) NO saludes de nuevo si ya están hablando. 2) NO digas el nombre de la persona todo el tiempo; habla directo de tú a tú. 3) NUNCA te disculpes ni digas que eres un bot o mensaje automático. 4) NO seas condescendiente. Responde al grano a lo que te acaban de decir. 5) Si no sabes algo: 'Dale, déjame y lo miro en un rato que me desocupe y te aviso'.") ?: ""
        set(value) = prefs.edit().putString("system_prompt", value).apply()

    // Active Day Status & Context (Novedades de hoy, estados de WhatsApp, fotos subidas)
    var currentStatusContext: String
        get() = prefs.getString("current_status_context", "") ?: ""
        set(value) = prefs.edit().putString("current_status_context", value.trim()).apply()

    // Schedule / Modo Horario
    var isScheduleEnabled: Boolean
        get() = prefs.getBoolean("schedule_enabled", true)
        set(value) = prefs.edit().putBoolean("schedule_enabled", value).apply()

    var startHour: Int
        get() = prefs.getInt("schedule_start_hour", 8) // 8:00 AM
        set(value) = prefs.edit().putInt("schedule_start_hour", value).apply()

    var startMinute: Int
        get() = prefs.getInt("schedule_start_minute", 0)
        set(value) = prefs.edit().putInt("schedule_start_minute", value).apply()

    var endHour: Int
        get() = prefs.getInt("schedule_end_hour", 20) // 8:00 PM
        set(value) = prefs.edit().putInt("schedule_end_hour", value).apply()

    var endMinute: Int
        get() = prefs.getInt("schedule_end_minute", 0)
        set(value) = prefs.edit().putInt("schedule_end_minute", value).apply()

    var outOfOfficeMessage: String
        get() = prefs.getString("out_of_office_msg",
            "¡Hola {nombre}! En este momento no estoy disponible (ya ando descansando / fuera de horario 🌙). Déjame tu mensaje con lo que necesitas y te respondo apenas me active 👍") ?: ""
        set(value) = prefs.edit().putString("out_of_office_msg", value).apply()

    // Anti-Ban & Filtering
    var ignoreGroups: Boolean
        get() = prefs.getBoolean("ignore_groups", true)
        set(value) = prefs.edit().putBoolean("ignore_groups", value).apply()

    var contactCooldownSeconds: Int
        get() = prefs.getInt("cooldown_seconds", 15)
        set(value) = prefs.edit().putInt("cooldown_seconds", value).apply()

    var minDelaySeconds: Int
        get() = prefs.getInt("min_delay_sec", 3)
        set(value) = prefs.edit().putInt("min_delay_sec", value).apply()

    var maxDelaySeconds: Int
        get() = prefs.getInt("max_delay_sec", 5)
        set(value) = prefs.edit().putInt("max_delay_sec", value).apply()

    // --- Custom Dynamic Rules ("Si preguntan X -> Responder Y") ---
    fun getCustomRules(): MutableList<CustomRule> {
        val json = prefs.getString("custom_rules_json", null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<CustomRule>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveCustomRules(rules: List<CustomRule>) {
        val json = gson.toJson(rules)
        prefs.edit().putString("custom_rules_json", json).apply()
    }

    fun addOrUpdateCustomRule(rule: CustomRule) {
        val rules = getCustomRules()
        val index = rules.indexOfFirst { it.id == rule.id || (it.trigger.isNotBlank() && it.trigger.equals(rule.trigger, ignoreCase = true)) }
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules.add(0, rule)
        }
        saveCustomRules(rules)
    }

    fun deleteCustomRule(ruleId: String) {
        val rules = getCustomRules()
        rules.removeAll { it.id == ruleId }
        saveCustomRules(rules)
    }

    fun toggleCustomRule(ruleId: String, enabled: Boolean) {
        val rules = getCustomRules()
        rules.find { it.id == ruleId }?.enabled = enabled
        saveCustomRules(rules)
    }

    // --- Copilot Chat History ---
    fun getCopilotHistory(): MutableList<CopilotMessage> {
        val json = prefs.getString("copilot_chat_json", null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<CopilotMessage>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveCopilotHistory(history: List<CopilotMessage>) {
        val trimmed = if (history.size > 60) history.takeLast(60) else history
        val json = gson.toJson(trimmed)
        prefs.edit().putString("copilot_chat_json", json).apply()
    }

    fun addCopilotMessage(sender: String, text: String): CopilotMessage {
        val history = getCopilotHistory()
        val msg = CopilotMessage(sender = sender, text = text)
        history.add(msg)
        saveCopilotHistory(history)
        return msg
    }

    fun clearCopilotHistory() {
        prefs.edit().remove("copilot_chat_json").apply()
    }

    fun clearCurrentStatus() {
        prefs.edit().remove("current_status_context").apply()
    }
}
