package com.personal.ircclient.core

import com.personal.ircclient.core.model.IrcConfig
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class IrcManager(private val repository: IrcRepository) {
    private val connections = ConcurrentHashMap<Long, IrcEngine>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _activeServers = MutableStateFlow<Set<Long>>(emptySet())
    val activeServers: StateFlow<Set<Long>> = _activeServers.asStateFlow()

    private val _globalStatuses = MutableStateFlow<Map<Long, IrcEngine.ConnectionStatus>>(emptyMap())
    val globalStatuses: StateFlow<Map<Long, IrcEngine.ConnectionStatus>> = _globalStatuses.asStateFlow()

    private var showEventsInRoom: Boolean = true

    fun setShowEventsInRoom(enabled: Boolean) {
        showEventsInRoom = enabled
        connections.values.forEach { it.showEventsInRoom = enabled }
    }

    fun connect(serverId: Long, config: IrcConfig): IrcEngine {
        val existing = connections[serverId]
        if (existing != null) {
            existing.connect()
            return existing
        }
        
        val engine = IrcEngine(serverId, config, repository)
        engine.showEventsInRoom = showEventsInRoom
        connections[serverId] = engine
        
        scope.launch {
            engine.connectionStatus.collect { status ->
                val current = _globalStatuses.value.toMutableMap()
                current[serverId] = status
                _globalStatuses.value = current
            }
        }

        engine.connect()
        
        _activeServers.value = connections.keys.toSet()
        return engine
    }

    fun disconnect(serverId: Long) {
        connections[serverId]?.disconnect()
        connections.remove(serverId)
        _activeServers.value = connections.keys.toSet()
        
        val current = _globalStatuses.value.toMutableMap()
        current.remove(serverId)
        _globalStatuses.value = current
    }

    fun getEngine(serverId: Long): IrcEngine? = connections[serverId]
    
    fun disconnectAll() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
        _activeServers.value = emptySet()
    }
}
