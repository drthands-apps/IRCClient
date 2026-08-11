package com.personal.ircclient.data.repository

import com.personal.ircclient.data.local.dao.*
import com.personal.ircclient.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class IrcRepository(
    private val serverDao: ServerDao,
    private val channelDao: ChannelDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val channelDiscoveryDao: ChannelDiscoveryDao,
    private val settingsDao: SettingsDao,
    private val asciiArtDao: AsciiArtDao,
    private val scriptDao: ScriptDao,
    private val radioStationDao: RadioStationDao
) {
    // Radio Stations
    val allRadioStations: Flow<List<RadioStationEntity>> = radioStationDao.getAll()
    suspend fun insertRadioStation(station: RadioStationEntity) = radioStationDao.insert(station)
    suspend fun deleteRadioStation(station: RadioStationEntity) = radioStationDao.delete(station)

    suspend fun checkAndInsertDefaultRadioStations() {
        if (radioStationDao.getCount() == 0L) {
            val defaults = listOf(
                RadioStationEntity(name = "Distrito Sonoro", url = "https://stream.zeno.fm/afd3qhkxz08uv", isDefault = true),
                RadioStationEntity(name = "Mundo Musica", url = "https://stream.radio-amistad.net:8002/stream", isDefault = true),
                RadioStationEntity(name = "Slay Radio", url = "http://slayradio.org:8000/", isDefault = true),
                RadioStationEntity(name = "Radio Paradise", url = "https://stream.radioparadise.com/mp3-192", isDefault = true),
                RadioStationEntity(name = "Groove Salad", url = "https://ice6.somafm.com/groovesalad-256-mp3", isDefault = true),
                RadioStationEntity(name = "DEF CON Radio", url = "https://ice6.somafm.com/defcon-256-mp3", isDefault = true),
                RadioStationEntity(name = "Deep Space One", url = "https://ice6.somafm.com/deepspaceone-128-mp3", isDefault = true),
                RadioStationEntity(name = "Drone Zone", url = "https://ice6.somafm.com/dronezone-128-mp3", isDefault = true)
            )
            defaults.forEach { radioStationDao.insert(it) }
        }
    }
    // Settings
    val settings: Flow<SettingsEntity?> = settingsDao.getSettings()
    suspend fun updateSettings(settings: SettingsEntity) = settingsDao.updateSettings(settings)

    // Scripts
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()
    suspend fun getActiveScripts() = scriptDao.getActiveScripts()
    suspend fun insertScript(script: ScriptEntity) = scriptDao.insertScript(script)
    suspend fun updateScript(script: ScriptEntity) = scriptDao.updateScript(script)
    suspend fun deleteScript(script: ScriptEntity) = scriptDao.deleteScript(script)

    // Servers
    val allServers: Flow<List<ServerEntity>> = serverDao.getAllServers()
    
    suspend fun insertServer(server: ServerEntity): Long = serverDao.insertServer(server)
    suspend fun updateServer(server: ServerEntity) = serverDao.updateServer(server)
    suspend fun deleteServer(server: ServerEntity) = serverDao.deleteServer(server)

    // Channels
    fun getChannelsForServer(serverId: Long): Flow<List<ChannelEntity>> = 
        channelDao.getChannelsForServer(serverId)

    fun getAllChannels(): Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    
    suspend fun getChannel(serverId: Long, name: String): ChannelEntity? = 
        channelDao.getChannel(serverId, name)
    
    suspend fun insertChannel(channel: ChannelEntity) = channelDao.insertChannel(channel)
    suspend fun updateChannel(channel: ChannelEntity) = channelDao.updateChannel(channel)
    suspend fun deleteChannel(channel: ChannelEntity) = channelDao.deleteChannel(channel)

    suspend fun clearInactiveTargets() {
        // Logic to clear messages for targets that don't have saveLog enabled
        // and aren't currently "joined" or active in some way.
        // For now, let's just clear messages where target is not in a 'saveLog' channel
        val channels = channelDao.getChannelsToClear()
        channels.forEach { channel ->
             messageDao.clearHistory(channel.serverId, channel.name)
        }
        // Also clear PMs that don't have a channel entry with saveLog = true
        messageDao.clearOrphanMessages()
    }

    // Messages
    fun getMessagesForTarget(serverId: Long, target: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForTarget(serverId, target)

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
        
        // Limit history lines if not marked to save log permanently
        val settings = settingsDao.getSettings().first()
        val limit = settings?.maxHistoryLines ?: 500
        messageDao.limitHistory(message.serverId, message.target, limit)
    }
    suspend fun clearHistory(serverId: Long, target: String) = messageDao.clearHistory(serverId, target)
    fun getAllActiveTargets() = messageDao.getAllActiveTargets()

    // Users
    fun getUsersForServer(serverId: Long): Flow<List<UserEntity>> = userDao.getUsersForServer(serverId)
    suspend fun getUser(nickname: String, serverId: Long) = userDao.getUser(nickname, serverId)
    suspend fun insertUser(user: UserEntity) = userDao.insertUser(user)
    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)
    
    suspend fun resetTemporalUserStates(serverId: Long) {
        userDao.resetTemporalIgnore(serverId)
        userDao.resetTemporalSilence(serverId)
    }

    suspend fun isHostmaskIgnored(serverId: Long, hostmask: String): Boolean {
        return userDao.isHostmaskIgnored(serverId, hostmask)
    }

    // Channel Discovery
    fun getDiscoveredChannels(serverId: Long) = channelDiscoveryDao.getDiscoveredChannels(serverId)
    suspend fun insertDiscovered(channel: ChannelDiscoveryEntity) = channelDiscoveryDao.insertDiscovered(channel)
    suspend fun clearDiscovered(serverId: Long) = channelDiscoveryDao.clearDiscovered(serverId)

    // ASCII Art & Phrases
    val asciiArt = asciiArtDao.getAll()
    suspend fun insertAsciiArt(item: AsciiArtEntity) {
        val count = asciiArtDao.getCount()
        if (count == 0L) {
            insertDefaultAscii()
        }
        asciiArtDao.insert(item)
    }

    private suspend fun insertDefaultAscii() {
        asciiArtDao.insert(AsciiArtEntity(name = "Hello", content = "Hello everyone! \\u000303Welcome!\\u000f", isPhrase = true))
        asciiArtDao.insert(AsciiArtEntity(name = "Status", content = "\\u0002FenixIRC\\u000f \\u000302Connected\\u000f", isPhrase = true))
        asciiArtDao.insert(AsciiArtEntity(name = "Small Bird", content = "  \\\\\\n (o>\\n// )\\n V_/_", isPhrase = false))
    }

    suspend fun deleteAsciiArt(item: AsciiArtEntity) = asciiArtDao.delete(item)

    // Script Defaults
    suspend fun checkAndInsertDefaultScripts() {
        val count = scriptDao.getAllScripts().first().size
        if (count == 0) {
            insertScript(ScriptEntity(
                name = "Quick Greet",
                content = "OUT: /hi -> PRIVMSG %T :Hello everyone! Greetings from %N.",
                triggerType = "OUTGOING"
            ))
            insertScript(ScriptEntity(
                name = "Private Msg Alias",
                content = "OUT: /p -> PRIVMSG %A",
                triggerType = "OUTGOING"
            ))
            insertScript(ScriptEntity(
                name = "Slap Action",
                content = "OUT: /slap -> PRIVMSG %T :\u0001ACTION slaps %A around a bit with a large trout\u0001",
                triggerType = "OUTGOING"
            ))
        }
    }
}
