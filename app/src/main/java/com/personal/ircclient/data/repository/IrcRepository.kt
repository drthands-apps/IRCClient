package com.personal.ircclient.data.repository

import com.personal.ircclient.data.local.dao.*
import com.personal.ircclient.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class IrcRepository(
    private val serverDao: ServerDao,
    private val channelDao: ChannelDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val channelDiscoveryDao: ChannelDiscoveryDao,
    private val settingsDao: SettingsDao
) {
    // Settings
    val settings: Flow<SettingsEntity?> = settingsDao.getSettings()
    suspend fun updateSettings(settings: SettingsEntity) = settingsDao.updateSettings(settings)

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

    suspend fun insertMessage(message: MessageEntity) = messageDao.insertMessage(message)
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
}
