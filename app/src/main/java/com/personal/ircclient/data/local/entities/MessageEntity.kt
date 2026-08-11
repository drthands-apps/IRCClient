package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val target: String, // Channel name or Nickname
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val isSystemMessage: Boolean = false,
    val isModifiedByScript: Boolean = false,
    val type: MessageType = MessageType.TEXT,
    val mediaUri: String? = null,
    val expiryTimestamp: Long? = null
)

enum class MessageType {
    TEXT, IMAGE, VOICE, FILE, JOIN, PART, QUIT, KICK, BAN, NICK, TOPIC, NOTICE
}
