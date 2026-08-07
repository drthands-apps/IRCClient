package com.personal.ircclient.data.repository

import com.personal.ircclient.data.local.dao.*
import com.personal.ircclient.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class IrcRepository(
    private val serverDao: ServerDao,
    private val channelDao: ChannelDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val channelDiscoveryDao: ChannelDiscoveryDao
) {
    // Servers
    val allServers: Flow<List<ServerEntity>> = serverDao.getAllServers()
    
    suspend fun insertServer(server: ServerEntity): Long = serverDao.insertServer(server)
    suspend fun updateServer(server: ServerEntity) = serverDao.updateServer(server)
    suspend fun deleteServer(server: ServerEntity) = serverDao.deleteServer(server)

    // Channels
    fun getChannelsForServer(serverId: Long): Flow<List<ChannelEntity>> = 
        channelDao.getChannelsForServer(serverId)
    
    suspend fun insertChannel(channel: ChannelEntity) = channelDao.insertChannel(channel)
    suspend fun updateChannel(channel: ChannelEntity) = channelDao.updateChannel(channel)
    suspend fun deleteChannel(channel: ChannelEntity) = channelDao.deleteChannel(channel)

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

    // Channel Discovery
    fun getDiscoveredChannels(serverId: Long) = channelDiscoveryDao.getDiscoveredChannels(serverId)
    suspend fun insertDiscovered(channel: ChannelDiscoveryEntity) = channelDiscoveryDao.insertDiscovered(channel)
    suspend fun clearDiscovered(serverId: Long) = channelDiscoveryDao.clearDiscovered(serverId)
}
