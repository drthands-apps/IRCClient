package com.personal.ircclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.personal.ircclient.data.local.dao.ChannelDao
import com.personal.ircclient.data.local.dao.ChannelDiscoveryDao
import com.personal.ircclient.data.local.dao.MessageDao
import com.personal.ircclient.data.local.dao.ServerDao
import com.personal.ircclient.data.local.dao.UserDao
import com.personal.ircclient.data.local.entities.*

@Database(
    entities = [ServerEntity::class, ChannelEntity::class, MessageEntity::class, UserEntity::class, ChannelDiscoveryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun channelDiscoveryDao(): ChannelDiscoveryDao
}
