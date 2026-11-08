package com.antigravity.whatsappbot

import java.util.concurrent.ConcurrentHashMap

data class ChatMessage(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

object ConversationMemory {
    // Contact key -> List of recent messages (up to 14 messages / lines)
    private val memory = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    // Increased to 14 to remember at least 12 previous lines/interactions
    private const val MAX_HISTORY_PER_CONTACT = 14
    private const val HISTORY_EXPIRATION_MS = 3 * 60 * 60 * 1000L // 3 hours

    fun addMessage(contact: String, role: String, text: String) {
        val list = memory.getOrPut(contact) { mutableListOf() }
        synchronized(list) {
            // Clear if stale (> 3 hours)
            if (list.isNotEmpty() && System.currentTimeMillis() - list.last().timestamp > HISTORY_EXPIRATION_MS) {
                list.clear()
            }
            list.add(ChatMessage(role, text))
            while (list.size > MAX_HISTORY_PER_CONTACT) {
                list.removeAt(0)
            }
        }
    }

    fun getHistory(contact: String): List<ChatMessage> {
        val list = memory[contact] ?: return emptyList()
        synchronized(list) {
            return list.toList()
        }
    }

    fun syncFromNotificationMessages(contact: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val list = memory.getOrPut(contact) { mutableListOf() }
        synchronized(list) {
            for (msg in messages) {
                if (list.none { it.text == msg.text && it.role == msg.role }) {
                    list.add(msg)
                }
            }
            while (list.size > MAX_HISTORY_PER_CONTACT) {
                list.removeAt(0)
            }
        }
    }

    fun clear(contact: String) {
        memory.remove(contact)
    }
}
