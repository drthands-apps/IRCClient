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
    val username: String = "irc_user",
    val realName: String = "IRC Client User",
    val password: String? = null,
    val useSsl: Boolean = false,
    val encoding: String = "UTF-8",
    val isAutoConnect: Boolean = false
)
