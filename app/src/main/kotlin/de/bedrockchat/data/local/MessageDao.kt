package de.bedrockchat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sortIndex ASC")
    suspend fun getByConversationId(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: Long)
}
