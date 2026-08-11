package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 6667,
    val nickname: String,
    val altNickname: String? = null,
    val generateRandomNick: Boolean = false,
    val username: String = "irc_user",
    val realName: String = "IRC Client User",
    val password: String? = null,
    val useSsl: Boolean = false,
    val allowPlainText: Boolean = true,
    val encoding: String = "UTF-8",
    val isAutoConnect: Boolean = false,
    val reconnectOpenChannels: Boolean = true,
    
    // SASL Authentication
    val useSasl: Boolean = false,
    val saslUsername: String? = null,
    val saslPassword: String? = null,
    
    // Bouncer
    val useBouncer: Boolean = false,
    val bouncerNetwork: String? = null,
    val lastConnected: Long = 0
)
