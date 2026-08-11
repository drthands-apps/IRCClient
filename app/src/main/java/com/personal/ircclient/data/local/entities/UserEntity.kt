package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserStatus {
    NONE, TEMPORAL, DEFINITIVE
}

@Entity(
    tableName = "users",
    primaryKeys = ["nickname", "serverId"]
)
data class UserEntity(
    val nickname: String,
    val serverId: Long,
    val hostmask: String? = null,
    val realName: String? = null,
    val isBlocked: Boolean = false,
    val ignoreStatus: UserStatus = UserStatus.NONE,
    val silenceStatus: UserStatus = UserStatus.NONE,
    val isFriend: Boolean = false,
    val encryptionKey: String? = null,
    val secureHandshakeStatus: HandshakeStatus = HandshakeStatus.NONE,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val lastVisited: Long = 0,
    val unreadCount: Int = 0
)

enum class HandshakeStatus {
    NONE, REQUESTED, RECEIVED, COMPLETED
}
