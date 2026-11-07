package com.antigravity.whatsappbot

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WhatsAppNotificationListener : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var prefs: AppPreferences
    private lateinit var antiBanEngine: AntiBanEngine
    private lateinit var scheduleManager: ScheduleManager
    private lateinit var geminiManager: GeminiManager
    private val ruleEngine = RuleEngine()

    // Debounce & Message Accumulation per Contact
    private val contactBuffers = ConcurrentHashMap<String, MutableList<String>>()
    private val contactJobs = ConcurrentHashMap<String, Job>()
    private val contactLastReplyActions = ConcurrentHashMap<String, NotificationUtils.ReplyAction>()
    private val lastProcessedHashes = ConcurrentHashMap<String, String>()
    private val manuallyPausedUntil = ConcurrentHashMap<String, Long>()

    // Debounce window in ms: waits for the user to finish typing multiple lines
    private val DEBOUNCE_DELAY_MS = 6000L
    private val MANUAL_PAUSE_DURATION_MS = 25 * 60 * 1000L // 25 minutes pause when user types

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
        Log.d(TAG, "WhatsApp Notification Service iniciado con Buffer Inteligente.")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        if (!prefs.isBotEnabled) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Ignore summary notifications
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // 1. Strict Group Filter
        val isGroup = extras.getBoolean("android.isGroupConversation", false) ||
                extras.containsKey("android.conversationTitle") ||
                extras.getCharSequence("android.conversationTitle") != null

        if (isGroup && prefs.ignoreGroups) {
            return
        }

        // 2. Extract sender and message text
        val rawSender = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""
        val rawMessage = extras.getCharSequence("android.text")?.toString() ?: ""

        if (rawSender.isBlank() || rawMessage.isBlank()) return

        // Ignore system notifications
        if (rawSender.equals("WhatsApp", ignoreCase = true) || rawMessage.contains("WhatsApp Web", ignoreCase = true)) {
            return
        }

        // 3. Detect Manual User Intervention (If Eduardo replied himself)
        if (rawSender.equals("Tú", ignoreCase = true) || rawSender.equals("Yo", ignoreCase = true) ||
            rawMessage.startsWith("Tú: ") || rawMessage.startsWith("Yo: ")) {
            // Cancel any pending bot reply for active contacts
            contactJobs.values.forEach { it.cancel() }
            contactBuffers.clear()
            broadcastLog("🛑 Detectada respuesta manual tuya en WhatsApp. Auto-respuesta en pausa temporal.")
            return
        }

        // Check if chat is in manual pause window
        val pauseUntil = manuallyPausedUntil[rawSender] ?: 0L
        if (System.currentTimeMillis() < pauseUntil) {
            val remainingMins = Math.ceil((pauseUntil - System.currentTimeMillis()) / 60000.0).toInt()
            broadcastLog("⏸️ Chat con $rawSender pausado ($remainingMins min restantes por intervención manual).")
            return
        }

        // 4. Duplicate Notification Filter (WhatsApp sends multiple events for the same message)
        val messageHash = "$rawSender:$rawMessage"
        val lastHash = lastProcessedHashes[rawSender]
        if (messageHash == lastHash) {
            return // Ignore duplicate notification event
        }
        lastProcessedHashes[rawSender] = messageHash

        // 5. Save latest Quick Reply action
        val replyAction = NotificationUtils.extractReplyAction(notification)
        if (replyAction != null) {
            contactLastReplyActions[rawSender] = replyAction
        }

        // 6. Accumulate message in contact buffer (Support for multi-line bursts)
        val buffer = contactBuffers.getOrPut(rawSender) { mutableListOf() }
        synchronized(buffer) {
            buffer.add(rawMessage.trim())
        }

        val cleanName = sanitizeName(rawSender)
        broadcastLog("📥 De $cleanName: \"$rawMessage\" (Acumulando mensajes...)")

        // 7. Debounce Timer: Cancel previous pending reply and wait for user to finish typing
        contactJobs[rawSender]?.cancel()

        contactJobs[rawSender] = scope.launch {
            try {
                // Wait debounce window so the contact can send consecutive messages
                delay(DEBOUNCE_DELAY_MS)

                // Check Cooldown
                if (!antiBanEngine.canReply(rawSender)) {
                    broadcastLog("⏳ Cooldown activo para $cleanName (15s). Esperando para evitar spam.")
                    contactBuffers.remove(rawSender)
                    return@launch
                }

                // Combine all accumulated lines into one coherent message
                val fullMessage = synchronized(buffer) {
                    val combined = buffer.joinToString(". ")
                    buffer.clear()
                    combined
                }

                if (fullMessage.isBlank()) return@launch

                val action = contactLastReplyActions[rawSender]
                if (action == null) {
                    broadcastLog("⚠️ No se encontró botón de respuesta rápida para $cleanName.")
                    return@launch
                }

                broadcastLog("🧠 Procesando mensaje completo de $cleanName: \"$fullMessage\"")

                // Determine Response
                var responseText = ""
                var modeUsed = ""

                if (scheduleManager.isOutsideSchedule()) {
                    responseText = scheduleManager.getOutOfOfficeMessage(cleanName)
                    modeUsed = "Fuera de Horario"
                } else if (prefs.isAiMode && prefs.geminiApiKey.isNotBlank()) {
                    try {
                        broadcastLog("🤖 Consultando Gemini IA...")
                        responseText = geminiManager.generateReply(cleanName, fullMessage)
                        modeUsed = "Gemini IA"
                    } catch (e: Exception) {
                        Log.e(TAG, "Error Gemini: ${e.message}")
                        broadcastLog("⚠️ Error IA (${e.message}). Usando regla local...")
                        responseText = ruleEngine.evaluate(cleanName, fullMessage)
                        modeUsed = "Regla Respaldo"
                    }
                } else {
                    responseText = ruleEngine.evaluate(cleanName, fullMessage)
                    modeUsed = "Regla Local"
                }

                if (responseText.isBlank()) return@launch

                // Simulated human typing delay
                val delayMs = antiBanEngine.getHumanTypingDelayMs()
                broadcastLog("⏱️ Simulando escritura (${delayMs / 1000.0}s) para $cleanName ($modeUsed)...")
                delay(delayMs)

                // Dispatch single reply
                val success = NotificationUtils.sendReply(applicationContext, action, responseText)
                if (success) {
                    antiBanEngine.recordReply(rawSender)
                    broadcastLog("📤 Respondido a $cleanName: \"$responseText\"")
                } else {
                    broadcastLog("❌ Falló el envío de respuesta a $cleanName.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en debounce worker: ${e.message}", e)
            } finally {
                contactJobs.remove(rawSender)
            }
        }
    }

    /**
     * Sanitizes contact names like "Bertha Yusbelis D1" -> "Bertha"
     * Removes store tags, emojis, and numbers.
     */
    private fun sanitizeName(fullName: String): String {
        val cleaned = fullName.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]"), "").trim()
        val tokens = cleaned.split("\\s+".toRegex()).filter { it.length >= 2 }

        // Take only the first recognizable name, avoiding words like D1, Store, etc.
        val firstName = tokens.firstOrNull { !it.equals("d", ignoreCase = true) && !it.equals("tienda", ignoreCase = true) }
        return firstName?.replaceFirstChar { it.uppercase() } ?: "amigo(a)"
    }

    private fun broadcastLog(message: String) {
        val intent = Intent(ACTION_NEW_LOG).apply {
            putExtra(EXTRA_LOG_MESSAGE, message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
