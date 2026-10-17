package de.bedrockchat.data.model

import java.util.UUID

enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant")
}

data class Message(
    val role: MessageRole,
    val text: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
    val id: String = UUID.randomUUID().toString()
)
