package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val nickname: String,
    val serverId: Long,
    val realName: String? = null,
    val isBlocked: Boolean = false,
    val encryptionKey: String? = null,
    val secureHandshakeStatus: HandshakeStatus = HandshakeStatus.NONE,
    val notes: String? = null
)

enum class HandshakeStatus {
    NONE, REQUESTED, RECEIVED, COMPLETED
}
