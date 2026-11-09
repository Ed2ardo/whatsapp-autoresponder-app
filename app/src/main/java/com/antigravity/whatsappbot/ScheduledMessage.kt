package com.antigravity.whatsappbot

import java.util.UUID

data class ScheduledMessage(
    val id: String = UUID.randomUUID().toString(),
    var contactNameOrPhone: String = "",
    var messageText: String = "",
    var scheduledTimestamp: Long = 0L,
    var isSent: Boolean = false,
    var isAutoReplyTrigger: Boolean = true
)
