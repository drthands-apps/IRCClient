package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_discovery")
data class ChannelDiscoveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val channelName: String,
    val userCount: Int,
    val topic: String
)
