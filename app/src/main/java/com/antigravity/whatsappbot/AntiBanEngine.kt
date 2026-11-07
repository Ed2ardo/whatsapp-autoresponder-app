package com.antigravity.whatsappbot

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class AntiBanEngine(private val prefs: AppPreferences) {
    private val contactCooldowns = ConcurrentHashMap<String, Long>()

    /**
     * Checks if we can reply to this contact without violating the cooldown window.
     */
    fun canReply(contactId: String): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = contactCooldowns[contactId] ?: 0L
        val cooldownMs = prefs.contactCooldownSeconds * 1000L

        return (now - lastTime) >= cooldownMs
    }

    /**
     * Records that a reply was just sent to this contact.
     */
    fun recordReply(contactId: String) {
        contactCooldowns[contactId] = System.currentTimeMillis()
    }

    /**
     * Calculates a human-like delay in milliseconds (e.g., between 3.0s and 5.0s).
     */
    fun getHumanTypingDelayMs(): Long {
        val min = prefs.minDelaySeconds * 1000L
        val max = prefs.maxDelaySeconds * 1000L
        return if (max > min) {
            Random.nextLong(min, max + 1)
        } else {
            min
        }
    }
}
