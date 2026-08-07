package com.personal.ircclient.data.local.dao

import androidx.room.*
import com.personal.ircclient.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE serverId = :serverId AND target = :target ORDER BY timestamp DESC")
    fun getMessagesForTarget(serverId: Long, target: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE serverId = :serverId AND target = :target")
    suspend fun clearHistory(serverId: Long, target: String)

    @Query("SELECT DISTINCT target, serverId FROM messages")
    fun getAllActiveTargets(): Flow<List<TargetInfo>>
}

data class TargetInfo(
    val target: String,
    val serverId: Long
)
