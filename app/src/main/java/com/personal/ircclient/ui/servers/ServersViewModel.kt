package com.personal.ircclient.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.ircclient.core.IrcManager
import com.personal.ircclient.data.local.entities.ChannelEntity
import com.personal.ircclient.data.local.entities.ServerEntity
import com.personal.ircclient.data.repository.IrcRepository
import com.personal.ircclient.core.utils.NickGenerator
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

    val settings = repository.settings

    fun getChannels(serverId: Long): Flow<List<ChannelEntity>> = 
        repository.getChannelsForServer(serverId)

    fun getDiscoveredChannels(serverId: Long) = repository.getDiscoveredChannels(serverId)

    fun refreshChannelList(serverId: Long) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            engine?.send("LIST")
        }
    }

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

    fun updateServer(server: ServerEntity) {
        viewModelScope.launch {
            repository.updateServer(server)
            
            // Map Entity to IrcConfig
            val config = com.personal.ircclient.core.model.IrcConfig(
                host = server.host,
                port = server.port,
                nickname = server.nickname,
                altNickname = server.altNickname,
                generateRandomNick = server.generateRandomNick,
                username = server.username,
                realName = server.realName,
                password = server.password,
                useSsl = server.useSsl,
                allowPlainText = server.allowPlainText,
                encoding = server.encoding,
                useSasl = server.useSasl,
                saslUsername = server.saslUsername,
                saslPassword = server.saslPassword,
                useBouncer = server.useBouncer,
                bouncerNetwork = server.bouncerNetwork
            )
            ircManager.updateConfig(server.id, config)
        }
    }

    fun connectServer(server: ServerEntity) {
        val finalNickname = if (server.generateRandomNick) {
            NickGenerator.generate()
        } else server.nickname

        val config = com.personal.ircclient.core.model.IrcConfig(
            host = server.host,
            port = server.port,
            nickname = finalNickname,
            altNickname = server.altNickname,
            generateRandomNick = server.generateRandomNick,
            username = server.username,
            realName = server.realName,
            password = server.password,
            useSsl = server.useSsl,
            allowPlainText = server.allowPlainText,
            encoding = server.encoding,
            useSasl = server.useSasl,
            saslUsername = server.saslUsername,
            saslPassword = server.saslPassword,
            useBouncer = server.useBouncer,
            bouncerNetwork = server.bouncerNetwork
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
