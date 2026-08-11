package com.personal.ircclient.ui.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.ircclient.core.IrcManager
import com.personal.ircclient.core.security.EncryptionManager
import com.personal.ircclient.data.local.dao.TargetInfo
import com.personal.ircclient.data.local.entities.*
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatsViewModel(
    private val repository: IrcRepository,
    private val ircManager: IrcManager,
    private val ttsManager: com.personal.ircclient.core.audio.TextToSpeechManager? = null
) : ViewModel() {

    private val _settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())
    val settingsState: StateFlow<SettingsEntity> = _settings.map { it ?: SettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    var isTtsActive by mutableStateOf(false)
        private set

    var showEventsInRoom by mutableStateOf(true)
        private set
    
    var joinDisplayMode by mutableStateOf(EventDisplayMode.ROOM)
        private set

    var partDisplayMode by mutableStateOf(EventDisplayMode.ROOM)
        private set

    var quitDisplayMode by mutableStateOf(EventDisplayMode.ROOM)
        private set
    
    var nickChangeDisplayMode by mutableStateOf(EventDisplayMode.ROOM)
        private set

    var kickDisplayMode by mutableStateOf(EventDisplayMode.ROOM)
        private set

    var banDisplayMode by mutableStateOf(EventDisplayMode.ROOM)
        private set

    init {
        viewModelScope.launch {
            repository.settings.collectLatest { s ->
                s?.let {
                    isTtsActive = it.isTtsActive
                    showEventsInRoom = it.showEventsInRoom
                    joinDisplayMode = EventDisplayMode.valueOf(it.joinDisplayMode)
                    partDisplayMode = EventDisplayMode.valueOf(it.partDisplayMode)
                    quitDisplayMode = EventDisplayMode.valueOf(it.quitDisplayMode)
                    nickChangeDisplayMode = EventDisplayMode.valueOf(it.nickChangeDisplayMode)
                    kickDisplayMode = EventDisplayMode.valueOf(it.kickDisplayMode)
                    banDisplayMode = EventDisplayMode.valueOf(it.banDisplayMode)
                    updateEngineSettings()
                }
            }
        }

        viewModelScope.launch {
            ircManager.activeServers.collect { serverIds ->
                serverIds.forEach { serverId ->
                    val engine = ircManager.getEngine(serverId) ?: return@forEach
                    launch {
                        engine.messages.collect { msg ->
                            if (isTtsActive && msg.command == "PRIVMSG") {
                                val sender = msg.prefix?.substringBefore("!") ?: "Unknown"
                                val text = msg.parameters.getOrNull(1) ?: ""
                                if (!text.startsWith("[ENC]")) {
                                    ttsManager?.speak("$sender says: $text")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateEngineSettings() {
        ircManager.setEventModes(
            join = joinDisplayMode,
            part = partDisplayMode,
            quit = quitDisplayMode,
            nick = nickChangeDisplayMode,
            kick = kickDisplayMode,
            ban = banDisplayMode
        )
    }

    private fun saveSettings() {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(
                current.copy(
                    isTtsActive = isTtsActive,
                    showEventsInRoom = showEventsInRoom,
                    joinDisplayMode = joinDisplayMode.name,
                    partDisplayMode = partDisplayMode.name,
                    quitDisplayMode = quitDisplayMode.name,
                    nickChangeDisplayMode = nickChangeDisplayMode.name,
                    kickDisplayMode = kickDisplayMode.name,
                    banDisplayMode = banDisplayMode.name,
                    ownMessageColor = current.ownMessageColor,
                    otherBubbleColor = current.otherBubbleColor,
                    ownBubbleColor = current.ownBubbleColor,
                    themeName = current.themeName,
                    useHighContrast = current.useHighContrast
                )
            )
        }
    }

    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        isTtsActive = enabled
        saveSettings()
    }

    fun setShowEventsInRoomEnabled(enabled: Boolean) {
        showEventsInRoom = enabled
        ircManager.setShowEventsInRoom(enabled)
        saveSettings()
    }

    fun setJoinDisplayEnabled(enabled: Boolean) {
        joinDisplayMode = if (enabled) EventDisplayMode.ROOM else EventDisplayMode.IGNORE
        updateEngineSettings()
        saveSettings()
    }

    fun setPartDisplayEnabled(enabled: Boolean) {
        partDisplayMode = if (enabled) EventDisplayMode.ROOM else EventDisplayMode.IGNORE
        updateEngineSettings()
        saveSettings()
    }

    fun setQuitDisplayEnabled(enabled: Boolean) {
        quitDisplayMode = if (enabled) EventDisplayMode.ROOM else EventDisplayMode.IGNORE
        updateEngineSettings()
        saveSettings()
    }

    fun setNickChangeDisplayEnabled(enabled: Boolean) {
        nickChangeDisplayMode = if (enabled) EventDisplayMode.ROOM else EventDisplayMode.IGNORE
        updateEngineSettings()
        saveSettings()
    }

    fun setKickDisplayEnabled(enabled: Boolean) {
        kickDisplayMode = if (enabled) EventDisplayMode.ROOM else EventDisplayMode.IGNORE
        updateEngineSettings()
        saveSettings()
    }

    fun setBanDisplayEnabled(enabled: Boolean) {
        banDisplayMode = if (enabled) EventDisplayMode.ROOM else EventDisplayMode.IGNORE
        updateEngineSettings()
        saveSettings()
    }

    fun disconnectAll() {
        viewModelScope.launch {
            val quitMsg = settingsState.value.defaultQuitMessage
            ircManager.disconnectAll(quitMsg)
        }
    }

    fun setSoundOnFriendJoin(enabled: Boolean) {
        updateSettings(settingsState.value.copy(soundOnFriendJoin = enabled))
    }

    fun setSoundOnPrivateMessage(enabled: Boolean) {
        updateSettings(settingsState.value.copy(soundOnPrivateMessage = enabled))
    }

    fun setSoundOnBan(enabled: Boolean) {
        updateSettings(settingsState.value.copy(soundOnBan = enabled))
    }

    fun getBanList(serverId: Long, channel: String): StateFlow<Set<String>> {
        val engine = ircManager.getEngine(serverId) ?: return MutableStateFlow(emptySet())
        // Remove automatic fetch here to prevent infinite loop
        return engine.banLists.map { it[normalizeTarget(channel)] ?: emptySet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    }

    fun refreshBanList(serverId: Long, channel: String) {
        ircManager.getEngine(serverId)?.fetchBanList(channel)
    }

    fun banUser(serverId: Long, channel: String, mask: String, active: Boolean) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            engine?.send("MODE $channel ${if (active) "+b" else "-b"} $mask")
        }
    }

    fun setOp(serverId: Long, channel: String, nickname: String, active: Boolean) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            engine?.send("MODE $channel ${if (active) "+o" else "-o"} $nickname")
        }
    }

    // ASCII Art & Phrases
    val asciiArt: StateFlow<List<AsciiArtEntity>> = repository.asciiArt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertAsciiArt(name: String, content: String, isPhrase: Boolean, id: Long = 0L) {
        viewModelScope.launch {
            repository.insertAsciiArt(AsciiArtEntity(id = id, name = name, content = content, isPhrase = isPhrase))
        }
    }

    fun deleteAsciiArt(item: AsciiArtEntity) {
        viewModelScope.launch {
            repository.deleteAsciiArt(item)
        }
    }

    // Scripts
    val allScripts: StateFlow<List<ScriptEntity>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertScript(name: String, content: String, trigger: String, id: Long = 0L) {
        viewModelScope.launch {
            repository.insertScript(ScriptEntity(id = id, name = name, content = content, triggerType = trigger))
            ircManager.refreshScripts()
        }
    }

    fun deleteScript(script: ScriptEntity) {
        viewModelScope.launch {
            repository.deleteScript(script)
            ircManager.refreshScripts()
        }
    }

    fun toggleScript(script: ScriptEntity) {
        viewModelScope.launch {
            repository.updateScript(script.copy(isActive = !script.isActive))
            ircManager.refreshScripts()
        }
    }

    // Radio Stations
    val allRadioStations: StateFlow<List<RadioStationEntity>> = repository.allRadioStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertRadioStation(name: String, url: String) {
        viewModelScope.launch {
            repository.insertRadioStation(RadioStationEntity(name = name, url = url))
        }
    }

    fun deleteRadioStation(station: RadioStationEntity) {
        viewModelScope.launch {
            repository.deleteRadioStation(station)
        }
    }

    fun sendAsciiArt(serverId: Long, target: String, item: AsciiArtEntity) {
        viewModelScope.launch {
            val lines = item.content.split("\n")
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    val parsedLine = line.replace("\\u0003", "\u0003")
                        .replace("\\u0002", "\u0002")
                        .replace("\\u001f", "\u001f")
                        .replace("\\u000f", "\u000f")
                    sendMessage(serverId, target, parsedLine)
                }
            }
        }
    }

    // --- Original UI Flow & Repo bridge ---
    
    val activeTargets: StateFlow<List<TargetInfo>> = repository.getAllActiveTargets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChannels: StateFlow<List<TargetInfo>> = activeTargets.map { targets ->
        targets.filter { isChannel(it.target) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePrivateChats: StateFlow<List<TargetInfo>> = activeTargets.map { targets ->
        targets.filter { !isChannel(it.target) && it.target != "Status" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statusTargets: StateFlow<List<TargetInfo>> = activeTargets.map { targets ->
        targets.filter { it.target == "Status" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allServers: StateFlow<List<ServerEntity>> = repository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalUnreadCount: StateFlow<Int> = activeTargets.map { targets ->
        targets.sumOf { it.unreadCount ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val channelsUnreadCount: StateFlow<Int> = activeChannels.map { targets ->
        targets.sumOf { it.unreadCount ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val privatesUnreadCount: StateFlow<Int> = activePrivateChats.map { targets ->
        targets.sumOf { it.unreadCount ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val statusUnreadCount: StateFlow<Int> = statusTargets.map { targets ->
        targets.sumOf { it.unreadCount ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun clearUnreadCount(serverId: Long, target: String) {
        ircManager.setCurrentlyViewing(serverId, target)
        viewModelScope.launch {
            val channel = repository.getChannel(serverId, target)
            if (channel != null) {
                repository.updateChannel(channel.copy(unreadCount = 0, lastVisited = System.currentTimeMillis()))
            }
        }
    }

    fun toggleFavorite(serverId: Long, target: String) {
        viewModelScope.launch {
            val channel = repository.getChannel(serverId, target)
            if (channel != null) {
                repository.updateChannel(channel.copy(isFavorite = !channel.isFavorite))
            } else {
                repository.insertChannel(ChannelEntity(serverId = serverId, name = target, isFavorite = true, isJoined = false))
            }
        }
    }

    fun joinFavoriteChannels() {
        viewModelScope.launch {
            val favorites = repository.getAllChannels().first().filter { it.isFavorite }
            favorites.forEach { fav ->
                val engine = ircManager.getEngine(fav.serverId)
                engine?.send("JOIN ${fav.name}")
            }
        }
    }

    fun joinRecentChannels() {
        viewModelScope.launch {
            val recents = repository.getAllChannels().first()
                .filter { it.lastVisited > 0 }
                .sortedByDescending { it.lastVisited }
                .take(5)
            recents.forEach { recent ->
                val engine = ircManager.getEngine(recent.serverId)
                engine?.send("JOIN ${recent.name}")
            }
        }
    }

    fun findUserInChannels(nickname: String): List<Pair<Long, String>> {
        val results = mutableListOf<Pair<Long, String>>()
        ircManager.globalStatuses.value.keys.forEach { serverId ->
            val engine = ircManager.getEngine(serverId)
            engine?.channelUsers?.value?.forEach { (channel, users) ->
                if (users.any { it.equals(nickname, ignoreCase = true) }) {
                    results.add(serverId to channel)
                }
            }
        }
        return results
    }

    val totalChannelUsersCount: StateFlow<Int> = ircManager.globalStatuses
        .flatMapLatest { statuses ->
            val engineFlows = statuses.keys.mapNotNull { ircManager.getEngine(it)?.channelUsers }
            if (engineFlows.isEmpty()) flowOf(0)
            else combine(engineFlows) { userMaps ->
                var total = 0
                userMaps.forEach { map ->
                    map.values.forEach { total += it.size }
                }
                total
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onLeaveChat(serverId: Long) {
        ircManager.setCurrentlyViewing(serverId, null)
    }

    fun closeChat(serverId: Long, target: String) {
        viewModelScope.launch {
            val channel = repository.getChannel(serverId, target)
            val settings = settingsState.value
            if (channel != null && channel.isJoined) {
                val engine = ircManager.getEngine(serverId)
                engine?.send("PART $target :${settings.defaultPartMessage}")
            }
            if (channel == null || !channel.saveLog) {
                repository.clearHistory(serverId, target)
            }
            if (channel != null) {
                 repository.updateChannel(channel.copy(unreadCount = 0, isJoined = false))
                 repository.deleteChannel(channel)
            }
        }
    }

    fun setSaveLog(serverId: Long, target: String, enabled: Boolean) {
        viewModelScope.launch {
            val channel = repository.getChannel(serverId, target)
            if (channel != null) {
                repository.updateChannel(channel.copy(saveLog = enabled))
            } else {
                repository.insertChannel(ChannelEntity(serverId = serverId, name = target, saveLog = enabled, isJoined = false))
            }
        }
    }

    fun setServerReconnectChannels(serverId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val server = repository.allServers.first().find { it.id == serverId }
            if (server != null) {
                repository.updateServer(server.copy(reconnectOpenChannels = enabled))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allChatUsers: StateFlow<List<UserEntity>> = repository.allServers
        .flatMapLatest { servers ->
            val flows = servers.map { repository.getUsersForServer(it.id) }
            if (flows.isEmpty()) flowOf(emptyList())
            else combine(flows) { it.toList().flatten() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lastUserRefresh = mutableMapOf<String, Long>()

    fun getChannelUsersWithInfo(serverId: Long, channelName: String): StateFlow<List<ChannelUserInfo>> {
        val engine = ircManager.getEngine(serverId) ?: return MutableStateFlow(emptyList())
        if (!isChannel(channelName)) return MutableStateFlow(emptyList())

        val normalizedName = normalizeTarget(channelName)
        
        val key = "${serverId}_$normalizedName"
        val now = System.currentTimeMillis()
        if (now - (lastUserRefresh[key] ?: 0L) > 30000) {
            viewModelScope.launch {
                engine.send("NAMES $channelName")
                lastUserRefresh[key] = now
            }
        }
        
        val filteredUsers = engine.channelUsers.map { it[normalizedName] ?: emptyList() }.distinctUntilChanged()
        val filteredPrefixes = engine.userPrefixes.map { it[normalizedName] ?: emptyMap() }.distinctUntilChanged()

        return combine(filteredUsers, filteredPrefixes) { nicks, prefixMap ->
            nicks.map { nick ->
                val userEntity = repository.getUser(nick, serverId)
                ChannelUserInfo(
                    nickname = nick,
                    prefix = prefixMap[nick] ?: "",
                    isFriend = userEntity?.isFriend ?: false,
                    ignoreStatus = userEntity?.ignoreStatus ?: UserStatus.NONE,
                    hostmask = userEntity?.hostmask
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getChannelUsers(serverId: Long, channelName: String): StateFlow<List<String>> {
        val engine = ircManager.getEngine(serverId)
        val normalizedName = normalizeTarget(channelName)
        return engine?.channelUsers?.map { it[normalizedName] ?: emptyList() }
            ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            ?: MutableStateFlow(emptyList())
    }

    fun isUserOp(serverId: Long, channelName: String, nickname: String): Flow<Boolean> {
        val engine = ircManager.getEngine(serverId) ?: return flowOf(false)
        return engine.userPrefixes.map { allPrefixes ->
            val channelPrefixes = allPrefixes[channelName] ?: emptyMap()
            val prefix = channelPrefixes[nickname] ?: ""
            prefix == "@" || prefix == "&" || prefix == "~"
        }
    }

    fun ignoreUser(serverId: Long, nickname: String, status: UserStatus) {
        viewModelScope.launch {
            val user = repository.getUser(nickname, serverId)
            if (user != null) repository.insertUser(user.copy(ignoreStatus = status))
            else repository.insertUser(UserEntity(nickname = nickname, serverId = serverId, ignoreStatus = status))
        }
    }

    fun silenceUser(serverId: Long, nickname: String, status: UserStatus) {
        viewModelScope.launch {
            val user = repository.getUser(nickname, serverId)
            if (user != null) repository.insertUser(user.copy(silenceStatus = status))
            else repository.insertUser(UserEntity(nickname = nickname, serverId = serverId, silenceStatus = status))
        }
    }

    fun setFriend(serverId: Long, nickname: String, isFriend: Boolean) {
        viewModelScope.launch {
            val user = repository.getUser(nickname, serverId)
            if (user != null) repository.insertUser(user.copy(isFriend = isFriend))
            else repository.insertUser(UserEntity(nickname = nickname, serverId = serverId, isFriend = isFriend))
        }
    }

    private fun isChannel(target: String): Boolean {
        return target.startsWith("#") || target.startsWith("&") || target.startsWith("+") || target.startsWith("!")
    }

    private fun normalizeTarget(target: String): String {
        return if (isChannel(target)) target.lowercase() else target
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMessages(serverId: Long, target: String): Flow<List<MessageEntity>> {
        val finalTarget = normalizeTarget(target)
        val ticker = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1000)
            }
        }
        
        val userFlow = flow { emit(repository.getUser(target, serverId)) }
        
        return combine(repository.getMessagesForTarget(serverId, finalTarget), ticker, userFlow) { messages, currentTime, user ->
            val key = user?.encryptionKey
            
            messages.filter { it.expiryTimestamp == null || it.expiryTimestamp > currentTime }
                .map { msg ->
                val decryptedText = if (msg.isEncrypted && key != null) {
                    EncryptionManager.decrypt(msg.text, key)
                } else {
                    msg.text
                }
                
                // Parse media tag: [MEDIA:TYPE:TTL=seconds:DATA] or [MEDIA:TYPE:DATA]
                if (decryptedText.startsWith("[MEDIA:") && decryptedText.endsWith("]")) {
                    val content = decryptedText.removePrefix("[MEDIA:").removeSuffix("]")
                    val parts = content.split(":", limit = 3)
                    
                    var type = MessageType.TEXT
                    var data = ""
                    var ttl: Long? = null
                    
                    if (parts.size >= 2) {
                        type = try { MessageType.valueOf(parts[0]) } catch(e: Exception) { MessageType.TEXT }
                        
                        if (parts.size == 3 && parts[1].startsWith("TTL=")) {
                            ttl = parts[1].removePrefix("TTL=").toLongOrNull()
                            data = parts[2]
                        } else {
                            data = parts.subList(1, parts.size).joinToString(":")
                        }
                    }

                    if (ttl != null && msg.expiryTimestamp == null) {
                        // This is the first time we see this TTL, update in DB
                        viewModelScope.launch {
                            repository.insertMessage(msg.copy(expiryTimestamp = msg.timestamp + (ttl * 1000)))
                        }
                    }

                    msg.copy(text = data, type = type)
                } else {
                    msg.copy(text = decryptedText)
                }
            }
        }
    }

    fun getUser(serverId: Long, nickname: String): Flow<UserEntity?> = flow { emit(repository.getUser(nickname, serverId)) }

    fun getServerName(serverId: Long): Flow<String> = repository.allServers.map { servers -> servers.find { it.id == serverId }?.name ?: "Server $serverId" }

    fun getAvailableCommands(serverId: Long, target: String, isOp: Boolean): List<String> {
        val engine = ircManager.getEngine(serverId)
        return engine?.getAvailableCommands(target, isOp) ?: emptyList()
    }

    fun getCurrentNickname(serverId: Long): StateFlow<String> {
        val engine = ircManager.getEngine(serverId)
        return engine?.currentNicknameFlow ?: MutableStateFlow("Unknown")
    }

    fun initiateSecureChat(serverId: Long, nickname: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                engine.send("PRIVMSG $nickname :\u0001IRC_SEC_REQ\u0001")
                repository.insertUser(UserEntity(nickname = nickname, serverId = serverId, secureHandshakeStatus = HandshakeStatus.REQUESTED))
            }
        }
    }

    fun acceptSecureChat(serverId: Long, nickname: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                val uniqueKey = EncryptionManager.generateRandomKey()
                engine.send("PRIVMSG $nickname :\u0001IRC_SEC_KEY $uniqueKey\u0001")
                repository.insertUser(UserEntity(nickname = nickname, serverId = serverId, encryptionKey = uniqueKey, secureHandshakeStatus = HandshakeStatus.COMPLETED))
            }
        }
    }

    fun sendMessage(serverId: Long, target: String, text: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                if (text.startsWith("/")) {
                    val handled = engine.executeCommand(target, text)
                    if (!handled) engine.send(text.substring(1))
                    
                    if (text.startsWith("/ME ", ignoreCase = true)) {
                        val action = text.substring(4)
                        val myNick = getCurrentNickname(serverId).value
                        repository.insertMessage(MessageEntity(serverId = serverId, target = target, sender = myNick, text = "* $myNick $action", type = MessageType.TEXT))
                    }
                    if (text.startsWith("/QUERY ", ignoreCase = true)) {
                         val newTarget = text.substring(7).trim().substringBefore(" ")
                         if (newTarget.isNotEmpty()) repository.insertMessage(MessageEntity(serverId = serverId, target = newTarget, sender = "System", text = "Started query with $newTarget", isSystemMessage = true))
                    }
                    return@launch
                }
                
                if (target == "Status") { engine.send(text); return@launch }
                val user = repository.getUser(target, serverId)
                val key = user?.encryptionKey
                val finalMessage = if (key != null) "[ENC] " + EncryptionManager.encrypt(text, key) else text
                engine.send("PRIVMSG $target :$finalMessage")
                val finalTarget = normalizeTarget(target)
                repository.insertMessage(MessageEntity(serverId = serverId, target = finalTarget, sender = "me", text = if (key != null) EncryptionManager.encrypt(text, key) else text, isEncrypted = key != null, type = MessageType.TEXT))
            }
        }
    }

    fun sendMedia(serverId: Long, target: String, type: MessageType, data: String, ttlSeconds: Long? = null) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                val user = repository.getUser(target, serverId)
                val key = user?.encryptionKey
                
                val ttlPrefix = if (ttlSeconds != null) "TTL=$ttlSeconds:" else ""
                val mediaTag = "[MEDIA:${type.name}:$ttlPrefix$data]"
                val finalMessage = if (key != null) "[ENC] " + EncryptionManager.encrypt(mediaTag, key) else mediaTag
                
                engine.send("PRIVMSG $target :$finalMessage")
                val finalTarget = normalizeTarget(target)
                
                val expiryTimestamp = if (ttlSeconds != null) System.currentTimeMillis() + (ttlSeconds * 1000) else null
                
                repository.insertMessage(
                    MessageEntity(
                        serverId = serverId,
                        target = finalTarget,
                        sender = "me",
                        text = if (key != null) EncryptionManager.encrypt(mediaTag, key) else mediaTag,
                        isEncrypted = key != null,
                        type = type,
                        expiryTimestamp = expiryTimestamp
                    )
                )
            }
        }
    }
}

data class ChannelUserInfo(
    val nickname: String,
    val prefix: String,
    val isFriend: Boolean,
    val ignoreStatus: UserStatus,
    val hostmask: String? = null
)
