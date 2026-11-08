package com.antigravity.whatsappbot

import android.app.Notification
import android.content.Intent
import android.os.Bundle
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
    // Pause duration when user manually replies: 1 minute (60,000 ms)
    private val MANUAL_PAUSE_DURATION_MS = 60 * 1000L

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

        val cleanName = sanitizeName(rawSender)

        // 3. Detect Manual User Intervention (If Eduardo replied himself)
        val isManualSender = rawSender.equals("Tú", ignoreCase = true) || rawSender.equals("Yo", ignoreCase = true)
        val isManualText = rawMessage.startsWith("Tú: ", ignoreCase = true) || rawMessage.startsWith("Yo: ", ignoreCase = true)

        if (isManualSender || isManualText) {
            val pauseUntil = System.currentTimeMillis() + MANUAL_PAUSE_DURATION_MS

            if (!isManualSender) {
                // Eduardo replied to this specific contact (rawSender = contact name, rawMessage = "Tú: ...")
                manuallyPausedUntil[rawSender] = pauseUntil
                contactJobs[rawSender]?.cancel()
                contactJobs.remove(rawSender)
                contactBuffers.remove(rawSender)
                broadcastLog("🛑 Respondiste manualmente a $cleanName. Auto-respuesta cancelada y chat suspendido por 1 min.")
            } else {
                // Sender is "Tú", pause all currently active contacts
                for (activeContact in contactBuffers.keys()) {
                    manuallyPausedUntil[activeContact] = pauseUntil
                    contactJobs[activeContact]?.cancel()
                    contactJobs.remove(activeContact)
                }
                contactBuffers.clear()
                broadcastLog("🛑 Detectada respuesta tuya en WhatsApp. Chats activos suspendidos por 1 min.")
            }
            return
        }

        // Also inspect MessagingStyle messages list to see if the latest message was sent by the user
        val messagesArray = extras.getParcelableArray("android.messages")
        if (messagesArray != null && messagesArray.isNotEmpty()) {
            val lastMsgBundle = messagesArray.lastOrNull() as? Bundle
            if (lastMsgBundle != null) {
                val senderPerson = lastMsgBundle.getCharSequence("sender")?.toString()?.trim() ?: ""
                val isSelf = senderPerson.isEmpty() ||
                        senderPerson.equals("Tú", ignoreCase = true) ||
                        senderPerson.equals("Yo", ignoreCase = true)
                if (isSelf) {
                    manuallyPausedUntil[rawSender] = System.currentTimeMillis() + MANUAL_PAUSE_DURATION_MS
                    contactJobs[rawSender]?.cancel()
                    contactJobs.remove(rawSender)
                    contactBuffers.remove(rawSender)
                    broadcastLog("🛑 Último mensaje en chat enviado por ti a $cleanName. Auto-respuesta cancelada y suspendida por 1 min.")
                    return
                }
            }

            // Synchronize up to 12 previous messages from notification history so Gemini never loses context
            val historyList = mutableListOf<ChatMessage>()
            val count = messagesArray.size
            val startIdx = Math.max(0, count - 12)
            for (i in startIdx until count) {
                val b = messagesArray[i] as? Bundle ?: continue
                val text = b.getCharSequence("text")?.toString()?.trim() ?: continue
                val senderPerson = b.getCharSequence("sender")?.toString()?.trim() ?: ""
                val isSelfMsg = senderPerson.isEmpty() || senderPerson.equals("Tú", true) || senderPerson.equals("Yo", true)
                val role = if (isSelfMsg) "model" else "user"
                if (text.isNotBlank()) {
                    historyList.add(ChatMessage(role, text))
                }
            }
            if (historyList.isNotEmpty()) {
                ConversationMemory.syncFromNotificationMessages(rawSender, historyList)
            }
        }

        // Also check android.textLines if available for multi-line context
        val textLines = extras.getCharSequenceArray("android.textLines")
        if (textLines != null && textLines.isNotEmpty()) {
            val linesHistory = mutableListOf<ChatMessage>()
            val startIdx = Math.max(0, textLines.size - 12)
            for (i in startIdx until textLines.size) {
                val line = textLines[i]?.toString()?.trim() ?: continue
                if (line.isNotBlank()) {
                    linesHistory.add(ChatMessage("user", line))
                }
            }
            if (linesHistory.isNotEmpty()) {
                ConversationMemory.syncFromNotificationMessages(rawSender, linesHistory)
            }
        }

        // Check if chat is in manual pause window (1 minute after Eduardo intervened)
        val pauseUntil = manuallyPausedUntil[rawSender] ?: 0L
        if (System.currentTimeMillis() < pauseUntil) {
            val remainingSecs = Math.max(1, ((pauseUntil - System.currentTimeMillis()) / 1000).toInt())
            broadcastLog("⏸️ Chat con $cleanName suspendido ($remainingSecs s restantes tras tu respuesta manual).")
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

        broadcastLog("📥 De $cleanName: \"$rawMessage\" (Acumulando mensajes...)")

        // 7. Debounce Timer: Cancel previous pending reply and wait for user to finish typing
        contactJobs[rawSender]?.cancel()

        contactJobs[rawSender] = scope.launch {
            try {
                // Wait debounce window so the contact can send consecutive messages
                delay(DEBOUNCE_DELAY_MS)

                // Check again if user manually intervened during debounce
                if (System.currentTimeMillis() < (manuallyPausedUntil[rawSender] ?: 0L)) {
                    broadcastLog("🛑 Cancelando auto-respuesta a $cleanName: respondiste manualmente en WhatsApp.")
                    contactBuffers.remove(rawSender)
                    return@launch
                }

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
                } else {
                    // Check custom dynamic rules (Option 3 / FAQ)
                    val customMatch = ruleEngine.evaluateCustomRules(fullMessage, prefs.getCustomRules())
                    if (customMatch != null) {
                        responseText = customMatch
                        modeUsed = "Regla Dinámica (FAQ)"
                    } else if (prefs.isAiMode && prefs.geminiApiKey.isNotBlank()) {
                        try {
                            broadcastLog("🤖 Consultando Gemini IA con Contexto y Estados...")
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
                }

                if (responseText.isBlank()) return@launch

                // Simulated human typing delay
                val delayMs = antiBanEngine.getHumanTypingDelayMs()
                broadcastLog("⏱️ Simulando escritura (${delayMs / 1000.0}s) para $cleanName ($modeUsed)...")
                delay(delayMs)

                // Final safety check: if Eduardo replied while typing delay was running, abort!
                if (System.currentTimeMillis() < (manuallyPausedUntil[rawSender] ?: 0L)) {
                    broadcastLog("🛑 Envío a $cleanName abortado: detectada tu respuesta manual durante el retardo.")
                    return@launch
                }

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
