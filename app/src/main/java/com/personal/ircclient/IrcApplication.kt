package com.personal.ircclient

import android.app.Application
import androidx.room.Room
import com.personal.ircclient.core.IrcManager
import com.personal.ircclient.core.audio.TextToSpeechManager
import com.personal.ircclient.data.local.AppDatabase
import com.personal.ircclient.data.repository.IrcRepository

class IrcApplication : Application() {

    lateinit var database: AppDatabase
    lateinit var repository: IrcRepository
    lateinit var ircManager: IrcManager
    lateinit var ttsManager: TextToSpeechManager

    override fun onCreate() {
        super.onCreate()
        
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "irc_database"
        )
        .fallbackToDestructiveMigration()
        .build()
        
        repository = IrcRepository(
            database.serverDao(),
            database.channelDao(),
            database.messageDao(),
            database.userDao(),
            database.channelDiscoveryDao(),
            database.settingsDao()
        )
        
        ircManager = IrcManager(this, repository)
        ttsManager = TextToSpeechManager(this)
    }

    override fun onTerminate() {
        ttsManager.shutdown()
        super.onTerminate()
    }
}
