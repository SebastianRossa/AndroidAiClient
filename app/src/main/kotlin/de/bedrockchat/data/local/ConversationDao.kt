package de.bedrockchat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    /** Direkt-UPDATE ohne vorheriges getById – verhindert Null-Lücke bei schnellen Schreibfolgen. */
    @Query("UPDATE conversations SET updatedAt = :updatedAt, messageCount = :messageCount WHERE id = :id")
    suspend fun updateStats(id: Long, updatedAt: Long, messageCount: Int)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
