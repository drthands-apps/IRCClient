package com.personal.ircclient.core

import android.content.Context
import android.content.Intent
import com.personal.ircclient.core.model.IrcConfig
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class IrcManager(private val context: Context, private val repository: IrcRepository) {
    private val connections = ConcurrentHashMap<Long, IrcEngine>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        scope.launch {
            repository.clearInactiveTargets()
        }
        scope.launch {
            repository.settings.collect { s ->
                s?.let {
                    setShowEventsInRoom(it.showEventsInRoom)
                    updateServiceState(it.runInBackground)
                }
            }
        }
    }

    private fun updateServiceState(runInBackground: Boolean) {
        val hasActiveConnections = connections.isNotEmpty()
        val intent = Intent(context, IrcService::class.java).apply {
            putExtra("server_count", connections.size)
        }
        
        // If runInBackground is enabled, we keep the service alive if there are connections OR if we want to stay resident
        if (runInBackground || hasActiveConnections) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    private val _activeServers = MutableStateFlow<Set<Long>>(emptySet())
    val activeServers: StateFlow<Set<Long>> = _activeServers.asStateFlow()

    private val _globalStatuses = MutableStateFlow<Map<Long, IrcEngine.ConnectionStatus>>(emptyMap())
    val globalStatuses: StateFlow<Map<Long, IrcEngine.ConnectionStatus>> = _globalStatuses.asStateFlow()

    private var showEventsInRoom: Boolean = true

    fun setShowEventsInRoom(enabled: Boolean) {
        showEventsInRoom = enabled
        connections.values.forEach { it.showEventsInRoom = enabled }
    }

    private var joinMode = com.personal.ircclient.data.local.entities.EventDisplayMode.ROOM
    private var partMode = com.personal.ircclient.data.local.entities.EventDisplayMode.ROOM
    private var quitMode = com.personal.ircclient.data.local.entities.EventDisplayMode.ROOM
    private var nickMode = com.personal.ircclient.data.local.entities.EventDisplayMode.ROOM
    private var kickMode = com.personal.ircclient.data.local.entities.EventDisplayMode.ROOM
    private var banMode = com.personal.ircclient.data.local.entities.EventDisplayMode.ROOM

    fun setEventModes(
        join: com.personal.ircclient.data.local.entities.EventDisplayMode,
        part: com.personal.ircclient.data.local.entities.EventDisplayMode,
        quit: com.personal.ircclient.data.local.entities.EventDisplayMode,
        nick: com.personal.ircclient.data.local.entities.EventDisplayMode,
        kick: com.personal.ircclient.data.local.entities.EventDisplayMode,
        ban: com.personal.ircclient.data.local.entities.EventDisplayMode
    ) {
        joinMode = join
        partMode = part
        quitMode = quit
        nickMode = nick
        kickMode = kick
        banMode = ban
        connections.values.forEach { it.updateEventSettings(join, part, quit, nick, kick, ban) }
    }

    fun connect(serverId: Long, config: IrcConfig): IrcEngine {
        val existing = connections[serverId]
        if (existing != null) {
            existing.connect()
            return existing
        }
        
        val engine = IrcEngine(context, serverId, config, repository)
        engine.showEventsInRoom = showEventsInRoom
        engine.updateEventSettings(joinMode, partMode, quitMode, nickMode, kickMode, banMode)
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
        scope.launch {
            val s = repository.settings.firstOrNull()
            if (s != null) updateServiceState(s.runInBackground)
        }
        return engine
    }

    fun disconnect(serverId: Long) {
        connections[serverId]?.disconnect()
        connections.remove(serverId)
        _activeServers.value = connections.keys.toSet()
        
        val current = _globalStatuses.value.toMutableMap()
        current.remove(serverId)
        _globalStatuses.value = current
        
        scope.launch {
            val s = repository.settings.firstOrNull()
            if (s != null) updateServiceState(s.runInBackground)
        }
    }

    fun getEngine(serverId: Long): IrcEngine? = connections[serverId]

    fun setCurrentlyViewing(serverId: Long, target: String?) {
        connections[serverId]?.currentlyViewingTarget = target
    }
    
    fun disconnectAll() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
        _activeServers.value = emptySet()
    }

    fun updateConfig(serverId: Long, config: IrcConfig) {
        connections[serverId]?.updateConfig(config)
    }

    fun refreshScripts() {
        connections.values.forEach { it.refreshScripts() }
    }
}
