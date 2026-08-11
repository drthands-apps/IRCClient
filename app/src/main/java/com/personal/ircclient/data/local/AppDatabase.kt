package com.personal.ircclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.personal.ircclient.data.local.dao.*
import com.personal.ircclient.data.local.entities.*

@Database(
    entities = [
        ServerEntity::class, 
        ChannelEntity::class, 
        MessageEntity::class, 
        UserEntity::class, 
        ChannelDiscoveryEntity::class,
        SettingsEntity::class,
        AsciiArtEntity::class,
        ScriptEntity::class,
        RadioStationEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun channelDiscoveryDao(): ChannelDiscoveryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun asciiArtDao(): AsciiArtDao
    abstract fun scriptDao(): ScriptDao
    abstract fun radioStationDao(): RadioStationDao
}
