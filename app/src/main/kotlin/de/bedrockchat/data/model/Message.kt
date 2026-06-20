package de.bedrockchat.data.model

import java.util.UUID

data class Message(
    val role: String,   // "user" | "assistant"
    val text: String,
    val id: String = UUID.randomUUID().toString()
)
