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

    @Query("""
        SELECT DISTINCT m.target, m.serverId, c.unreadCount, c.isJoined, c.isBanned, c.saveLog, c.topic, c.isFavorite, c.lastVisited
        FROM messages m
        LEFT JOIN channels c ON m.serverId = c.serverId AND m.target = c.name
    """)
    fun getAllActiveTargets(): Flow<List<TargetInfo>>

    @Query("DELETE FROM messages WHERE NOT EXISTS (SELECT 1 FROM channels WHERE channels.serverId = messages.serverId AND channels.name = messages.target AND channels.saveLog = 1)")
    suspend fun clearOrphanMessages()
}

data class TargetInfo(
    val target: String,
    val serverId: Long,
    val unreadCount: Int?,
    val isJoined: Boolean?,
    val isBanned: Boolean?,
    val saveLog: Boolean?,
    val topic: String?,
    val isFavorite: Boolean? = false,
    val lastVisited: Long? = 0L
)
