package com.antigravity.whatsappbot

import java.util.UUID

data class CustomRule(
    val id: String = UUID.randomUUID().toString(),
    var trigger: String = "",
    var response: String = "",
    var enabled: Boolean = true
)
