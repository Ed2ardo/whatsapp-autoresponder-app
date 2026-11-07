package com.antigravity.whatsappbot

import java.util.concurrent.ConcurrentHashMap

data class ChatMessage(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

object ConversationMemory {
    // Contact key -> List of recent messages (max 6)
    private val memory = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    private const val MAX_HISTORY_PER_CONTACT = 6
    private const val HISTORY_EXPIRATION_MS = 2 * 60 * 60 * 1000L // 2 hours

    fun addMessage(contact: String, role: String, text: String) {
        val list = memory.getOrPut(contact) { mutableListOf() }
        synchronized(list) {
            // Clear if stale (> 2 hours)
            if (list.isNotEmpty() && System.currentTimeMillis() - list.last().timestamp > HISTORY_EXPIRATION_MS) {
                list.clear()
            }
            list.add(ChatMessage(role, text))
            if (list.size > MAX_HISTORY_PER_CONTACT) {
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

    fun clear(contact: String) {
        memory.remove(contact)
    }
}
