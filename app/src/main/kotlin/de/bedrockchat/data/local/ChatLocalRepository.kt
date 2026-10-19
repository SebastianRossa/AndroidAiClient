package de.bedrockchat.data.local

import androidx.room.withTransaction
import de.bedrockchat.data.model.Message
import de.bedrockchat.data.model.MessageRole
import kotlinx.coroutines.flow.Flow

/**
 * Lokales Repository für Konversations- und Nachrichtenpersistenz via Room.
 */
class ChatLocalRepository(private val db: ChatDatabase) {

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().getAllFlow()

    /** Einmaliger Snapshot aller Konversationen (für initialen Ladevorgang). */
    suspend fun getAllConversationsList(): List<ConversationEntity> =
        db.conversationDao().getAll()

    /**
     * Erstellt eine neue Konversation und gibt die generierte ID zurück.
     * Der Titel wird aus dem ersten Nutzer-Text abgeleitet (max. 60 Zeichen).
     */
    suspend fun createConversation(firstMessageText: String): Long {
        val raw = firstMessageText.trim()
        val title = if (raw.length > 60) raw.take(60) + "…" else raw.ifBlank { "Neues Gespräch" }
        return db.conversationDao().insert(
            ConversationEntity(
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Persistiert alle Nachrichten einer Konversation (ersetzte vorherige Einträge)
     * und aktualisiert den Zeitstempel sowie die Anzahl der Nachrichten.
     * Alle Operationen laufen in einer einzigen DB-Transaktion – der InvalidationTracker
     * feuert genau einmal beim Commit, nie auf Zwischenzuständen.
     */
    suspend fun saveMessages(conversationId: Long, messages: List<Message>) {
        val entities = messages.mapIndexed { index, msg ->
            MessageEntity(
                id = msg.id,
                conversationId = conversationId,
                role = msg.role.value,
                text = msg.text,
                imageBase64 = msg.imageBase64,
                imageMimeType = msg.imageMimeType,
                sortIndex = index
            )
        }
        db.withTransaction {
            db.messageDao().deleteByConversationId(conversationId)
            db.messageDao().insertAll(entities)
            db.conversationDao().updateStats(
                id = conversationId,
                updatedAt = System.currentTimeMillis(),
                messageCount = messages.size
            )
        }
    }

    /** Lädt alle Nachrichten einer Konversation als [Message]-Liste. */
    suspend fun loadMessages(conversationId: Long): List<Message> =
        db.messageDao().getByConversationId(conversationId).map { entity ->
            Message(
                role = MessageRole.entries.first { it.value == entity.role },
                text = entity.text,
                imageBase64 = entity.imageBase64,
                imageMimeType = entity.imageMimeType,
                id = entity.id
            )
        }

    /** Löscht eine Konversation samt aller zugehörigen Nachrichten. */
    suspend fun deleteConversation(conversationId: Long) {
        db.messageDao().deleteByConversationId(conversationId)
        db.conversationDao().deleteById(conversationId)
    }
}
