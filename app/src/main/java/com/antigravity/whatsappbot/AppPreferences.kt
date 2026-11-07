package com.antigravity.whatsappbot

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wa_bot_prefs", Context.MODE_PRIVATE)

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
            "Eres Eduardo respondiendo en tu propio WhatsApp personal. Tono: cercano, amigable, relajado y breve (máximo 1 o 2 oraciones, como un chat real de WhatsApp). Si te preguntan qué haces, diles que andas ocupado en el computador. Si no sabes algo o te piden algo específico, di que lo revisarás apenas te desocupes. NUNCA respondas como robot corporativo ni uses listas.") ?: ""
        set(value) = prefs.edit().putString("system_prompt", value).apply()

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
}
