package com.personal.ircclient.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.ircclient.core.IrcManager
import com.personal.ircclient.data.local.entities.ChannelEntity
import com.personal.ircclient.data.local.entities.ServerEntity
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ServersViewModel(
    private val repository: IrcRepository,
    private val ircManager: IrcManager
) : ViewModel() {

    val servers: StateFlow<List<ServerEntity>> = repository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeServerIds: StateFlow<Set<Long>> = ircManager.activeServers
    
    val connectionStatuses: StateFlow<Map<Long, com.personal.ircclient.core.IrcEngine.ConnectionStatus>> = ircManager.globalStatuses

    fun getChannels(serverId: Long): Flow<List<ChannelEntity>> = 
        repository.getChannelsForServer(serverId)

    fun getDiscoveredChannels(serverId: Long) = repository.getDiscoveredChannels(serverId)

    fun joinChannel(serverId: Long, channelName: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (channelName.startsWith("/")) {
                val command = channelName.substring(1)
                engine?.send(command)
            } else {
                engine?.send("JOIN $channelName")
            }
        }
    }

    fun addServer(server: ServerEntity) {
        viewModelScope.launch {
            repository.insertServer(server)
        }
    }

    fun connectServer(server: ServerEntity) {
        // Map ServerEntity to IrcConfig (simplified here)
        val config = com.personal.ircclient.core.model.IrcConfig(
            host = server.host,
            port = server.port,
            nickname = server.nickname,
            username = server.username,
            realName = server.realName,
            password = server.password,
            useSsl = server.useSsl,
            encoding = server.encoding
        )
        ircManager.connect(server.id, config)
    }

    fun disconnectServer(serverId: Long) {
        ircManager.disconnect(serverId)
    }

    fun getEngine(serverId: Long): com.personal.ircclient.core.IrcEngine? = ircManager.getEngine(serverId)
    
    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            ircManager.disconnect(server.id)
            repository.deleteServer(server)
        }
    }
}
