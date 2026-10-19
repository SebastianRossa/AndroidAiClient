package de.bedrockchat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: Long,
    val role: String,
    val text: String,
    val imageBase64: String?,
    val imageMimeType: String?,
    val sortIndex: Int
)
