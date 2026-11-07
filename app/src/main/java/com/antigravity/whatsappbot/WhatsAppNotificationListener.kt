package com.antigravity.whatsappbot

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WhatsAppNotificationListener : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var prefs: AppPreferences
    private lateinit var antiBanEngine: AntiBanEngine
    private lateinit var scheduleManager: ScheduleManager
    private lateinit var geminiManager: GeminiManager
    private val ruleEngine = RuleEngine()

    companion object {
        const val ACTION_NEW_LOG = "com.antigravity.whatsappbot.NEW_LOG"
        const val EXTRA_LOG_MESSAGE = "extra_log_message"
        private const val TAG = "WABotService"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        antiBanEngine = AntiBanEngine(prefs)
        scheduleManager = ScheduleManager(prefs)
        geminiManager = GeminiManager(prefs)
        Log.d(TAG, "WhatsApp Notification Service iniciado.")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Only process WhatsApp or WhatsApp Business
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        // 2. Master toggle check
        if (!prefs.isBotEnabled) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Ignore summary notifications (notification stacks)
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // 3. Strict Group Filter
        val isGroup = extras.getBoolean("android.isGroupConversation", false) ||
                extras.containsKey("android.conversationTitle") ||
                extras.getCharSequence("android.conversationTitle") != null

        if (isGroup && prefs.ignoreGroups) {
            return
        }

        // 4. Extract sender name and message content
        val senderTitle = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""
        val messageText = extras.getCharSequence("android.text")?.toString() ?: ""

        if (senderTitle.isBlank() || messageText.isBlank()) return

        // Ignore system messages like "Copia de seguridad", "WhatsApp Web activo", etc.
        if (senderTitle.equals("WhatsApp", ignoreCase = true) || messageText.contains("WhatsApp Web", ignoreCase = true)) {
            return
        }

        // 5. Anti-Ban Filter: Cooldown per contact
        if (!antiBanEngine.canReply(senderTitle)) {
            broadcastLog("⏳ Cooldown activo para $senderTitle. Mensaje ignorado para evitar spam.")
            return
        }

        // 6. Find Quick Reply RemoteInput Action
        val replyAction = NotificationUtils.extractReplyAction(notification) ?: return

        // Launch coroutine to process and respond with human delay
        scope.launch {
            try {
                broadcastLog("📩 De $senderTitle: \"$messageText\"")

                // Determine Response Text
                var responseText = ""
                var modeUsed = ""

                // Check Schedule / Modo Horario first
                if (scheduleManager.isOutsideSchedule()) {
                    responseText = scheduleManager.getOutOfOfficeMessage(senderTitle)
                    modeUsed = "Modo Fuera de Horario"
                } else if (prefs.isAiMode && prefs.geminiApiKey.isNotBlank()) {
                    try {
                        broadcastLog("🤖 Consultando Gemini IA para $senderTitle...")
                        responseText = geminiManager.generateReply(senderTitle, messageText)
                        modeUsed = "IA Gemini"
                    } catch (e: Exception) {
                        Log.e(TAG, "Error llamando Gemini: ${e.message}")
                        broadcastLog("⚠️ Error IA (${e.message}). Aplicando respuesta de respaldo...")
                        responseText = ruleEngine.evaluate(senderTitle, messageText)
                        modeUsed = "Regla de Respaldo"
                    }
                } else {
                    responseText = ruleEngine.evaluate(senderTitle, messageText)
                    modeUsed = "Reglas Locales"
                }

                if (responseText.isBlank()) return@launch

                // Calculate and apply simulated human delay
                val delayMs = antiBanEngine.getHumanTypingDelayMs()
                broadcastLog("⏱️ Simulando escritura humana (${delayMs / 1000.0}s) hacia $senderTitle ($modeUsed)...")
                delay(delayMs)

                // Dispatch native Quick Reply
                val success = NotificationUtils.sendReply(applicationContext, replyAction, responseText)
                if (success) {
                    antiBanEngine.recordReply(senderTitle)
                    broadcastLog("📤 Respondido a $senderTitle: \"$responseText\"")
                } else {
                    broadcastLog("❌ Error enviando respuesta rápida a $senderTitle.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación: ${e.message}", e)
                broadcastLog("❌ Error inesperado: ${e.message}")
            }
        }
    }

    private fun broadcastLog(message: String) {
        val intent = Intent(ACTION_NEW_LOG).apply {
            putExtra(EXTRA_LOG_MESSAGE, message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
