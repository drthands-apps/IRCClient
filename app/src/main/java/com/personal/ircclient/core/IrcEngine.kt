package com.personal.ircclient.core

import com.personal.ircclient.core.commands.CommandHandler
import com.personal.ircclient.core.model.IrcConfig
import com.personal.ircclient.core.model.IrcMessage
import com.personal.ircclient.core.security.EncryptionManager
import com.personal.ircclient.data.local.entities.*
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class IrcEngine(
    val serverId: Long,
    private val config: IrcConfig,
    private val repository: IrcRepository? = null,
    private val sniffer: ScriptSniffer = ScriptSniffer()
) {
    private val commandHandler = CommandHandler(this, repository)

    private var joinMode = EventDisplayMode.ROOM
    private var partMode = EventDisplayMode.ROOM
    private var quitMode = EventDisplayMode.ROOM
    private var nickChangeMode = EventDisplayMode.ROOM
    private var kickMode = EventDisplayMode.ROOM
    private var banMode = EventDisplayMode.ROOM
    
    var showEventsInRoom: Boolean = true
        set(value) {
            field = value
            val mode = if (value) EventDisplayMode.ROOM else EventDisplayMode.STATUS
            updateEventSettings(mode, mode, mode, mode, mode, mode)
        }

    fun updateEventSettings(
        join: EventDisplayMode,
        part: EventDisplayMode,
        quit: EventDisplayMode,
        nick: EventDisplayMode,
        kick: EventDisplayMode,
        ban: EventDisplayMode
    ) {
        joinMode = join
        partMode = part
        quitMode = quit
        nickChangeMode = nick
        kickMode = kick
        banMode = ban
    }

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    
    private val _messages = MutableSharedFlow<IrcMessage>()
    val messages: SharedFlow<IrcMessage> = _messages.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _channelUsers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val channelUsers: StateFlow<Map<String, List<String>>> = _channelUsers.asStateFlow()

    private val _userPrefixes = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val userPrefixes: StateFlow<Map<String, Map<String, String>>> = _userPrefixes.asStateFlow()

    private var job: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastMessageTime = System.currentTimeMillis()
    private val motdBuffer = StringBuilder()

    private val _currentNicknameFlow = MutableStateFlow(config.nickname)
    val currentNicknameFlow: StateFlow<String> = _currentNicknameFlow.asStateFlow()

    private var currentNickname: String = config.nickname
        set(value) {
            field = value
            _currentNicknameFlow.value = value
        }

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, REGISTERING, REGISTERED, ERROR
    }

    fun connect() {
        job?.cancel()
        heartbeatJob?.cancel()
        job = scope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                logToStatus("Connecting to ${config.host}:${config.port}...")
                
                val rawSocket = if (config.useSsl) {
                    SSLSocketFactory.getDefault().createSocket()
                } else {
                    Socket()
                }
                
                socket = rawSocket.apply {
                    connect(InetSocketAddress(config.host, config.port), 15000)
                    soTimeout = 0 
                }

                reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), config.encoding))
                writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream(), config.encoding))

                _connectionStatus.value = ConnectionStatus.CONNECTED
                logToStatus("Connected to socket. Starting registration...")
                lastMessageTime = System.currentTimeMillis()

                startHeartbeat()

                _connectionStatus.value = ConnectionStatus.REGISTERING
                // Initial registration
                if (config.password != null) {
                    send("PASS ${config.password}")
                }
                send("NICK ${config.nickname}")
                send("USER ${config.username} 0 * :${config.realName}")

                listen()
            } catch (e: Exception) {
                e.printStackTrace()
                logToStatus("Connection error: ${e.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                disconnect()
            }
        }
    }

    private suspend fun logToStatus(text: String) {
        repository?.insertMessage(
            MessageEntity(
                serverId = serverId,
                target = "Status",
                sender = "System",
                text = text,
                isSystemMessage = true
            )
        )
    }

    private fun isChannel(target: String): Boolean {
        return target.startsWith("#") || target.startsWith("&") || target.startsWith("+") || target.startsWith("!")
    }

    private fun normalizeTarget(target: String): String {
        return if (isChannel(target)) target.lowercase() else target
    }

    private suspend fun logEvent(channel: String?, text: String, mode: EventDisplayMode, type: MessageType) {
        if (mode == EventDisplayMode.IGNORE) return
        
        val target = if (mode == EventDisplayMode.ROOM && channel != null) {
            normalizeTarget(channel)
        } else "Status"

        repository?.insertMessage(
            MessageEntity(
                serverId = serverId,
                target = target,
                sender = "System",
                text = text,
                isSystemMessage = true,
                type = type
            )
        )
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(60000) 
                if (System.currentTimeMillis() - lastMessageTime > 120000) { 
                    logToStatus("Ping timeout. Disconnecting.")
                    _connectionStatus.value = ConnectionStatus.ERROR
                    disconnect()
                    break
                }
                send("PING ${config.host}")
            }
        }
    }

    private suspend fun listen() {
        withContext(Dispatchers.IO) {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    lastMessageTime = System.currentTimeMillis()
                    
                    val rawMessage = IrcMessage.parse(line) ?: continue
                    val message = sniffer.onIncomingMessage(rawMessage) ?: continue
                    
                    handleProtocol(message)
                    saveMessageIfNecessary(message)
                    _messages.emit(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                disconnect()
            }
        }
    }

    private suspend fun handleProtocol(message: IrcMessage) {
        when (message.command) {
            "PING" -> {
                val response = message.parameters.firstOrNull() ?: ""
                send("PONG :$response")
            }
            "375" -> { // RPL_MOTDSTART
                motdBuffer.clear()
                motdBuffer.append(message.parameters.getOrNull(1) ?: "").append("\n")
            }
            "372" -> { // RPL_MOTD
                motdBuffer.append(message.parameters.getOrNull(1) ?: "").append("\n")
            }
            "376" -> { // RPL_ENDOFMOTD
                motdBuffer.append(message.parameters.getOrNull(1) ?: "")
                repository?.insertMessage(
                    MessageEntity(
                        serverId = serverId,
                        target = "Status",
                        sender = "Server",
                        text = motdBuffer.toString(),
                        isSystemMessage = true,
                        type = MessageType.TOPIC
                    )
                )
                motdBuffer.clear()
            }
            "001" -> { // RPL_WELCOME
                currentNickname = message.parameters.firstOrNull() ?: config.nickname
                _connectionStatus.value = ConnectionStatus.REGISTERED
                logToStatus("Successfully registered as $currentNickname.")
            }
            "321" -> { // RPL_LISTSTART
                repository?.clearDiscovered(serverId)
            }
            "322" -> { // RPL_LIST
                val channelName = message.parameters.getOrNull(1) ?: return
                val userCount = message.parameters.getOrNull(2)?.toIntOrNull() ?: 0
                val topic = message.parameters.getOrNull(3) ?: ""
                repository?.insertDiscovered(
                    ChannelDiscoveryEntity(
                        serverId = serverId,
                        channelName = channelName,
                        userCount = userCount,
                        topic = topic
                    )
                )
            }
            "323" -> { // RPL_LISTEND
                logToStatus("Channel list finished.")
            }
            "332" -> { // RPL_TOPIC
                val channel = message.parameters.getOrNull(1) ?: return
                val topic = message.parameters.getOrNull(2) ?: ""
                updateChannelTopic(channel, topic)
                logEvent(channel, "Topic for $channel: $topic", EventDisplayMode.ROOM, MessageType.TOPIC)
            }
            "333" -> { // RPL_TOPICWHOTIME
                val channel = message.parameters.getOrNull(1) ?: return
                val setter = message.parameters.getOrNull(2) ?: ""
                logEvent(channel, "Topic set by $setter", EventDisplayMode.ROOM, MessageType.TOPIC)
            }
            "353" -> { // RPL_NAMREPLY
                val channel = message.parameters.getOrNull(2) ?: return
                val rawUsers = message.parameters.getOrNull(3)?.split(" ")?.filter { it.isNotEmpty() } ?: return
                
                val currentUsers = _channelUsers.value.toMutableMap()
                val currentPrefixes = _userPrefixes.value.toMutableMap()
                
                val userList = mutableListOf<String>()
                val prefixMap = currentPrefixes[channel]?.toMutableMap() ?: mutableMapOf()

                rawUsers.forEach { raw ->
                    val prefix = if (raw.startsWith("@") || raw.startsWith("+") || raw.startsWith("%") || raw.startsWith("&") || raw.startsWith("~")) {
                        raw.substring(0, 1)
                    } else ""
                    val nick = if (prefix.isNotEmpty()) raw.substring(1) else raw
                    userList.add(nick)
                    prefixMap[nick] = prefix
                }

                currentUsers[channel] = ((currentUsers[channel] ?: emptyList()) + userList).distinct()
                currentPrefixes[channel] = prefixMap
                
                _channelUsers.value = currentUsers
                _userPrefixes.value = currentPrefixes
            }
            "JOIN" -> {
                val channelName = message.parameters.firstOrNull() ?: return
                val sender = message.prefix?.substringBefore("!") ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                val existing = current[channelName] ?: emptyList()
                current[channelName] = (existing + sender).distinct()
                _channelUsers.value = current

                logEvent(channelName, "$sender joined $channelName", joinMode, MessageType.JOIN)

                if (sender == currentNickname) {
                    updateChannelStatus(channelName, joined = true, banned = false)
                }
            }
            "PART" -> {
                val channelName = message.parameters.firstOrNull() ?: return
                val sender = message.prefix?.substringBefore("!") ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                current[channelName] = (current[channelName] ?: emptyList()) - sender
                _channelUsers.value = current

                logEvent(channelName, "$sender left $channelName", partMode, MessageType.PART)

                if (sender == currentNickname) {
                    updateChannelStatus(channelName, joined = false)
                }
            }
            "KICK" -> {
                val channelName = message.parameters.getOrNull(0) ?: return
                val kickedUser = message.parameters.getOrNull(1) ?: return
                val reason = message.parameters.getOrNull(2) ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                current[channelName] = (current[channelName] ?: emptyList()) - kickedUser
                _channelUsers.value = current

                logEvent(channelName, "$kickedUser was kicked by ${message.prefix?.substringBefore("!")} ($reason)", kickMode, MessageType.KICK)

                if (kickedUser == currentNickname) {
                    updateChannelStatus(channelName, joined = false, banned = true)
                }
            }
            "QUIT" -> {
                val sender = message.prefix?.substringBefore("!") ?: ""
                val reason = message.parameters.firstOrNull() ?: ""
                val current = _channelUsers.value.toMutableMap()
                for (entry in current) {
                    if (entry.value.contains(sender)) {
                        current[entry.key] = entry.value - sender
                        logEvent(entry.key, "$sender quit ($reason)", quitMode, MessageType.QUIT)
                    }
                }
                _channelUsers.value = current
            }
            "NICK" -> {
                val oldNick = message.prefix?.substringBefore("!") ?: ""
                val newNick = message.parameters.firstOrNull() ?: return
                
                if (oldNick == currentNickname) {
                    currentNickname = newNick
                }

                val current = _channelUsers.value.toMutableMap()
                for (entry in current) {
                    if (entry.value.contains(oldNick)) {
                        current[entry.key] = (entry.value - oldNick) + newNick
                        logEvent(entry.key, "$oldNick is now known as $newNick", nickChangeMode, MessageType.NICK)
                    }
                }
                _channelUsers.value = current
            }
            "MODE" -> {
                val channel = message.parameters.getOrNull(0) ?: return
                val mode = message.parameters.getOrNull(1) ?: ""
                val target = message.parameters.getOrNull(2) ?: ""
                
                if (mode == "+b" && target.contains(currentNickname)) {
                    logEvent(channel, "You have been BANNED from $channel", EventDisplayMode.ROOM, MessageType.BAN)
                    updateChannelStatus(channel, joined = false, banned = true)
                } else if (mode.contains("b")) {
                    logEvent(channel, "Mode change: $mode $target", banMode, MessageType.BAN)
                } else {
                    logEvent(channel, "Mode change: $mode $target", kickMode, MessageType.KICK)
                }
            }
        }
    }

    private suspend fun handleCtcp(sender: String, ctcp: String) {
        if (repository == null) return
        
        when {
            ctcp == "IRC_SEC_REQ" -> {
                repository.insertUser(
                    UserEntity(
                        nickname = sender,
                        serverId = serverId,
                        secureHandshakeStatus = HandshakeStatus.RECEIVED
                    )
                )
                logToStatus("Secure chat request received from $sender.")
            }
            ctcp.startsWith("IRC_SEC_KEY ") -> {
                val key = ctcp.removePrefix("IRC_SEC_KEY ").trim()
                repository.insertUser(
                    UserEntity(
                        nickname = sender,
                        serverId = serverId,
                        encryptionKey = key,
                        secureHandshakeStatus = HandshakeStatus.COMPLETED
                    )
                )
                logToStatus("Secure chat established with $sender.")
            }
        }
    }

    private suspend fun saveMessageIfNecessary(message: IrcMessage) {
        if (repository == null) return

        when (message.command) {
            "PRIVMSG" -> {
                val rawTarget = message.parameters.getOrNull(0) ?: return
                var text = message.parameters.getOrNull(1) ?: ""
                val sender = message.prefix?.substringBefore("!") ?: "system"
                
                // Check ignore
                val user = repository.getUser(sender, serverId)
                if (user?.isIgnored == true) return

                if (text.startsWith("\u0001") && text.endsWith("\u0001")) {
                    val ctcp = text.substring(1, text.length - 1)
                    if (ctcp.startsWith("ACTION ")) {
                        val actionText = ctcp.substring(7)
                        val finalTarget = if (rawTarget.equals(currentNickname, ignoreCase = true)) {
                            sender
                        } else {
                            normalizeTarget(rawTarget)
                        }
                        repository.insertMessage(
                            MessageEntity(
                                serverId = serverId,
                                target = finalTarget,
                                sender = sender,
                                text = "* $sender $actionText",
                                isSystemMessage = false,
                                type = MessageType.TEXT
                            )
                        )
                        return
                    }
                    handleCtcp(sender, ctcp)
                    return
                }

                val isEncrypted = text.startsWith("[ENC] ")
                if (isEncrypted) {
                    text = text.removePrefix("[ENC] ").trim()
                }

                val finalTarget = if (rawTarget.equals(currentNickname, ignoreCase = true)) {
                    sender
                } else {
                    normalizeTarget(rawTarget)
                }

                incrementUnreadCount(finalTarget)

                repository.insertMessage(
                    MessageEntity(
                        serverId = serverId,
                        target = finalTarget,
                        sender = sender,
                        text = text,
                        isEncrypted = isEncrypted,
                        isModifiedByScript = message.isModifiedByScript,
                        type = MessageType.TEXT
                    )
                )
            }
            "NOTICE" -> {
                val target = message.parameters.getOrNull(0) ?: "Status"
                val text = message.parameters.getOrNull(1) ?: ""
                val sender = message.prefix?.substringBefore("!") ?: "System"
                
                val finalTarget = if (target == currentNickname || target == "*") "Status" else normalizeTarget(target)

                repository.insertMessage(
                    MessageEntity(
                        serverId = serverId,
                        target = finalTarget,
                        sender = sender,
                        text = "NOTICE: $text",
                        isSystemMessage = true,
                        isModifiedByScript = message.isModifiedByScript
                    )
                )
            }
            else -> {
                if (message.command.all { it.isDigit() }) {
                    val text = message.parameters.drop(1).joinToString(" ")
                    val targetNick = message.parameters.getOrNull(1)
                    
                    val finalTarget = when (message.command) {
                        "311", "312", "317", "318", "319", "301" -> targetNick ?: "Status"
                        else -> "Status"
                    }
                    
                    repository?.insertMessage(
                        MessageEntity(
                            serverId = serverId,
                            target = finalTarget,
                            sender = "System",
                            text = text,
                            isSystemMessage = true,
                            isModifiedByScript = message.isModifiedByScript
                        )
                    )
                }
            }
        }
    }

    suspend fun send(raw: String) {
        val finalRaw = sniffer.onOutgoingMessage(raw) ?: return
        withContext(Dispatchers.IO) {
            try {
                writer?.write("$finalRaw\r\n")
                writer?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun executeCommand(target: String, input: String): Boolean {
        return commandHandler.handleCommand(target, input)
    }

    fun getAvailableCommands(target: String, isOp: Boolean): List<String> {
        return commandHandler.getAvailableCommands(target, isOp)
    }

    suspend fun sendMessage(message: IrcMessage) {
        send(message.build())
    }

    private suspend fun updateChannelStatus(name: String, joined: Boolean, banned: Boolean? = null) {
        val channel = repository?.getChannel(serverId, name)
        if (channel != null) {
            repository.updateChannel(channel.copy(
                isJoined = joined,
                isBanned = banned ?: channel.isBanned
            ))
        } else {
            repository?.insertChannel(
                ChannelEntity(
                    serverId = serverId,
                    name = name,
                    isJoined = joined,
                    isBanned = banned ?: false
                )
            )
        }
    }

    private suspend fun updateChannelTopic(name: String, topic: String) {
        val channel = repository?.getChannel(serverId, name)
        if (channel != null) {
            repository.updateChannel(channel.copy(topic = topic))
        } else {
            repository?.insertChannel(
                ChannelEntity(
                    serverId = serverId,
                    name = name,
                    topic = topic,
                    isJoined = true
                )
            )
        }
    }

    private suspend fun incrementUnreadCount(name: String) {
        if (name == "Status") return
        val channel = repository?.getChannel(serverId, name)
        if (channel != null) {
            repository.updateChannel(channel.copy(unreadCount = channel.unreadCount + 1))
        } else {
            repository?.insertChannel(
                ChannelEntity(
                    serverId = serverId,
                    name = name,
                    unreadCount = 1,
                    isJoined = false
                )
            )
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        scope.launch {
            try {
                socket?.close()
                reader?.close()
                writer?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket = null
                reader = null
                writer = null
                _connectionStatus.emit(ConnectionStatus.DISCONNECTED)
            }
        }
    }
}
