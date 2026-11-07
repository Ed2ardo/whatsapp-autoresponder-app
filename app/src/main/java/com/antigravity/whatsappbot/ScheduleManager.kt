package com.antigravity.whatsappbot

import java.util.Calendar

class ScheduleManager(private val prefs: AppPreferences) {

    /**
     * Returns true if the schedule filter is enabled AND the current time
     * is OUTSIDE the allowed active window.
     */
    fun isOutsideSchedule(): Boolean {
        if (!prefs.isScheduleEnabled) return false

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val startMinutes = prefs.startHour * 60 + prefs.startMinute
        val endMinutes = prefs.endHour * 60 + prefs.endMinute

        return if (startMinutes <= endMinutes) {
            // Normal day schedule e.g., 08:00 to 20:00
            currentMinutes < startMinutes || currentMinutes >= endMinutes
        } else {
            // Overnight schedule e.g., 22:00 to 06:00
            currentMinutes in endMinutes until startMinutes
        }
    }

    /**
     * Generates a natural out-of-office message with variables formatted.
     */
    fun getOutOfOfficeMessage(contactName: String): String {
        val raw = prefs.outOfOfficeMessage
        val cleanName = if (contactName.isNotBlank()) contactName else "amigo(a)"
        return raw.replace("{nombre}", cleanName, ignoreCase = true)
                  .replace("{name}", cleanName, ignoreCase = true)
    }
}
