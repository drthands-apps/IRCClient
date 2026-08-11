package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val password: String? = null,
    val isJoined: Boolean = false,
    val autoJoin: Boolean = true,
    val isBanned: Boolean = false,
    val unreadCount: Int = 0,
    val saveLog: Boolean = false,
    val topic: String? = null,
    val isFavorite: Boolean = false,
    val lastVisited: Long = 0
)
