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
        DELETE FROM messages 
        WHERE serverId = :serverId AND target = :target 
        AND id NOT IN (
            SELECT id FROM messages 
            WHERE serverId = :serverId AND target = :target 
            ORDER BY timestamp DESC LIMIT :limit
        )
    """)
    suspend fun limitHistory(serverId: Long, target: String, limit: Int)

    @Query("""
        SELECT DISTINCT t.target, t.serverId, 
               COALESCE(c.unreadCount, u.unreadCount, 0) as unreadCount,
               c.isJoined, c.isBanned, c.saveLog, c.topic,
               COALESCE(c.isFavorite, u.isFavorite, 0) as isFavorite,
               COALESCE(c.lastVisited, u.lastVisited, 0) as lastVisited,
               u.encryptionKey, u.secureHandshakeStatus
        FROM (
            SELECT target, serverId FROM messages WHERE target NOT LIKE 'WHOIS %'
            UNION
            SELECT name as target, serverId FROM channels WHERE (isFavorite = 1 OR lastVisited > 0) AND name NOT LIKE 'WHOIS %'
            UNION
            SELECT nickname as target, serverId FROM users WHERE (isFavorite = 1 OR lastVisited > 0) AND nickname NOT LIKE 'WHOIS %'
        ) t
        LEFT JOIN channels c ON t.serverId = c.serverId AND t.target = c.name
        LEFT JOIN users u ON t.serverId = u.serverId AND t.target = u.nickname
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
    val lastVisited: Long? = 0L,
    val encryptionKey: String? = null,
    val secureHandshakeStatus: com.personal.ircclient.data.local.entities.HandshakeStatus? = null
)
