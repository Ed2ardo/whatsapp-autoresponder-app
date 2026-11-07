package com.antigravity.whatsappbot

import java.util.UUID

data class CopilotMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String = "copilot", // "user" or "copilot"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CopilotResponse(
    val replyToUser: String,
    val updatedStatusSummary: String? = null,
    val newCustomRule: CustomRule? = null
)
