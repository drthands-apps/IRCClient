package com.personal.ircclient

import android.app.Application
import androidx.room.Room
import com.personal.ircclient.core.IrcManager
import com.personal.ircclient.core.audio.TextToSpeechManager
import com.personal.ircclient.data.local.AppDatabase
import com.personal.ircclient.data.repository.IrcRepository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class IrcApplication : Application() {

    lateinit var database: AppDatabase
    lateinit var repository: IrcRepository
    lateinit var ircManager: IrcManager
    lateinit var ttsManager: TextToSpeechManager
    lateinit var fsrManager: com.personal.ircclient.core.network.FsrManager

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
            database.settingsDao(),
            database.asciiArtDao(),
            database.scriptDao(),
            database.radioStationDao()
        )
        
        ircManager = IrcManager(this, repository)
        ttsManager = TextToSpeechManager(this)
        // Stable alternative to Glitch
        fsrManager = com.personal.ircclient.core.network.FsrManager("wss://fenix-relay.onrender.com")

        applicationScope.launch(Dispatchers.IO) {
            insertDefaultServers()
            repository.checkAndInsertDefaultScripts()
            repository.checkAndInsertDefaultAscii()
            repository.checkAndInsertDefaultRadioStations()
            
            // Critical: Force sniffer to load the newly inserted default scripts
            ircManager.refreshScripts()
        }
    }

    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private suspend fun insertDefaultServers() {
        val servers = repository.allServers.first()
        if (servers.isEmpty()) {
            repository.insertServer(com.personal.ircclient.data.local.entities.ServerEntity(
                name = "Chateamos",
                host = "irc.chateamos.org",
                port = 6667,
                nickname = "FenixUser",
                generateRandomNick = true,
                encoding = "windows-1252"
            ))
            repository.insertServer(com.personal.ircclient.data.local.entities.ServerEntity(
                name = "ChatHispano",
                host = "irc.chathispano.com",
                port = 6667,
                nickname = "FenixUser",
                username = "fenix_rand",
                generateRandomNick = true
            ))
            repository.insertServer(com.personal.ircclient.data.local.entities.ServerEntity(
                name = "Libera.Chat",
                host = "irc.libera.chat",
                port = 6697,
                nickname = "FenixUser",
                useSsl = true
            ))
            repository.insertServer(com.personal.ircclient.data.local.entities.ServerEntity(
                name = "DALnet",
                host = "irc.dal.net",
                port = 6667,
                nickname = "FenixUser"
            ))
        }
    }

    override fun onTerminate() {
        ttsManager.shutdown()
        super.onTerminate()
    }
}
