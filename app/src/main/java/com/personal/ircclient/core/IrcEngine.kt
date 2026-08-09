package com.personal.ircclient.core

import com.personal.ircclient.core.commands.CommandHandler
import com.personal.ircclient.core.model.IrcConfig
import com.personal.ircclient.core.model.IrcMessage
import com.personal.ircclient.data.local.entities.*
import com.personal.ircclient.data.repository.IrcRepository
import com.personal.ircclient.core.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class IrcEngine(
    private val context: android.content.Context,
    val serverId: Long,
    private var config: IrcConfig,
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

    fun updateConfig(newConfig: IrcConfig) {
        val oldHost = config.host
        val oldPort = config.port
        val oldNick = config.nickname
        
        config = newConfig
        
        if (oldHost != newConfig.host || oldPort != newConfig.port) {
            // Need reconnect
            scope.launch {
                logToStatus("Server configuration changed (host/port). Reconnecting...")
                disconnect()
                connect()
            }
        } else if (oldNick != newConfig.nickname) {
            // Just update nick
            scope.launch {
                send("NICK ${newConfig.nickname}")
            }
        }
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

    private val _currentNicknameFlow = MutableStateFlow(config.nickname)
    val currentNicknameFlow: StateFlow<String> = _currentNicknameFlow.asStateFlow()

    private var currentNickname: String = config.nickname
        set(value) {
            field = value
            _currentNicknameFlow.value = value
        }

    var currentlyViewingTarget: String? = null

    private var job: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastMessageTime = System.currentTimeMillis()
    private val motdBuffer = StringBuilder()

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, REGISTERING, REGISTERED, ERROR
    }

    fun connect() {
        if (!config.useSsl && !config.allowPlainText) {
            scope.launch {
                logToStatus("Connection refused: Plain text connections are disabled for this server.")
            }
            return
        }
        job?.cancel()
        heartbeatJob?.cancel()
        job = scope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                logToStatus("Connecting to ${config.host}:${config.port}...")
                
                val settings = repository?.settings?.firstOrNull()
                val rawSocket = if (settings?.useProxy == true && settings.proxyHost.isNotEmpty()) {
                    val proxyType = if (settings.proxyType == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
                    val proxy = Proxy(proxyType, InetSocketAddress(settings.proxyHost, settings.proxyPort))
                    Socket(proxy)
                } else if (config.useSsl) {
                    SSLSocketFactory.getDefault().createSocket()
                } else {
                    Socket()
                }
                
                socket = rawSocket.apply {
                    connect(InetSocketAddress(config.host, config.port), 15000)
                    soTimeout = 0 
                    keepAlive = true
                    tcpNoDelay = true
                }

                reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), config.encoding))
                writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream(), config.encoding))

                _connectionStatus.value = ConnectionStatus.CONNECTED
                logToStatus("Connected to socket. Starting registration...")
                lastMessageTime = System.currentTimeMillis()

                startHeartbeat()

                _connectionStatus.value = ConnectionStatus.REGISTERING
                
                send("CAP LS 302")

                // Initial registration
                if (config.password != null) {
                    val pass = if (config.useBouncer && config.bouncerNetwork != null) {
                        "${config.password}/${config.bouncerNetwork}"
                    } else {
                        config.password
                    }
                    send("PASS $pass")
                }
                send("NICK ${config.nickname}")
                val realName = settings?.customUserAgent ?: config.realName
                val username = if (config.useBouncer && config.bouncerNetwork != null && config.password == null) {
                    "${config.username}/${config.bouncerNetwork}"
                } else {
                    config.username
                }
                send("USER $username 0 * :$realName")

                listen()
            } catch (e: Exception) {
                e.printStackTrace()
                logToStatus("Connection error: ${e.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                disconnect()
            }
        }
    }

    suspend fun logSystemMessage(target: String, text: String) {
        repository?.insertMessage(
            MessageEntity(
                serverId = serverId,
                target = target,
                sender = "System",
                text = text,
                isSystemMessage = true
            )
        )
    }

    private suspend fun logToStatus(text: String) {
        logSystemMessage("Status", text)
    }

    private fun isChannel(target: String): Boolean {
        return target.startsWith("#") || target.startsWith("&") || target.startsWith("+") || target.startsWith("!")
    }

    private fun normalizeTarget(target: String): String {
        return if (isChannel(target)) target.lowercase() else target
    }

    private suspend fun logEvent(channel: String?, text: String, mode: EventDisplayMode, type: MessageType) {
        if (mode == EventDisplayMode.IGNORE) return
        if (!showEventsInRoom) return

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
                if (System.currentTimeMillis() - lastMessageTime > 300000) { // 5 mins leniency for background
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
                    
                    // Filter out channel list from sharing to Status/UI flow
                    if (message.command == "322" || message.command == "321" || message.command == "323") {
                        handleProtocol(message)
                        continue
                    }

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
            "CAP" -> {
                val subCommand = message.parameters.getOrNull(1)
                if (subCommand == "LS") {
                    val caps = message.parameters.getOrNull(2) ?: ""
                    val req = mutableListOf<String>()
                    if (caps.contains("sasl") && config.useSasl) req.add("sasl")
                    if (caps.contains("multi-prefix")) req.add("multi-prefix")
                    if (caps.contains("message-tags")) req.add("message-tags")
                    if (caps.contains("away-notify")) req.add("away-notify")
                    if (caps.contains("account-notify")) req.add("account-notify")
                    if (caps.contains("extended-join")) req.add("extended-join")
                    
                    if (req.isNotEmpty()) {
                        send("CAP REQ :${req.joinToString(" ")}")
                    } else {
                        send("CAP END")
                    }
                } else if (subCommand == "ACK") {
                    val acknowledged = message.parameters.getOrNull(2) ?: ""
                    if (acknowledged.contains("sasl")) {
                        send("AUTHENTICATE PLAIN")
                    } else {
                        send("CAP END")
                    }
                } else if (subCommand == "NAK") {
                    send("CAP END")
                }
            }
            "AUTHENTICATE" -> {
                if (message.parameters.firstOrNull() == "+") {
                    val authStr = "${config.saslUsername}\u0000${config.saslUsername}\u0000${config.saslPassword}"
                    val base64 = android.util.Base64.encodeToString(authStr.toByteArray(), android.util.Base64.NO_WRAP)
                    send("AUTHENTICATE $base64")
                }
            }
            "903" -> { // RPL_SASLSUCCESS
                logToStatus("SASL authentication successful.")
                send("CAP END")
            }
            "904", "905" -> { // ERR_SASLFAIL, ERR_SASLTOOLONG
                logToStatus("SASL authentication failed.")
                send("CAP END")
            }
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

                // Reset temporal states
                repository?.resetTemporalUserStates(serverId)

                // Sync friends for notification
                scope.launch {
                    val settings = repository?.settings?.firstOrNull()
                    if (settings?.enableFriendNotify == true) {
                        repository?.getUsersForServer(serverId)?.firstOrNull()?.filter { it.isFriend }?.forEach { friend ->
                            // Use NOTIFY or WATCH based on common server support
                            send("NOTIFY ${friend.nickname}")
                        }
                    }
                }

                // Auto-join channels
                val server = repository?.allServers?.firstOrNull()?.find { it.id == serverId }
                if (server?.reconnectOpenChannels == true) {
                    repository?.getChannelsForServer(serverId)?.firstOrNull()?.forEach { channel ->
                        if (channel.isJoined) {
                            send("JOIN ${channel.name}")
                        }
                    }
                }
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
                updateChannelTopic(normalizeTarget(channel), topic)
                logEvent(channel, "Topic for $channel: $topic", EventDisplayMode.ROOM, MessageType.TOPIC)
            }
            "333" -> { // RPL_TOPICWHOTIME
                val channel = message.parameters.getOrNull(1) ?: return
                val setter = message.parameters.getOrNull(2) ?: ""
                logEvent(channel, "Topic set by $setter", EventDisplayMode.ROOM, MessageType.TOPIC)
            }
            "353" -> { // RPL_NAMREPLY
                val channelRaw = message.parameters.getOrNull(2) ?: return
                val channel = normalizeTarget(channelRaw)
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
                val channelRaw = message.parameters.firstOrNull() ?: return
                val channelName = normalizeTarget(channelRaw)
                val sender = message.prefix?.substringBefore("!") ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                val existing = current[channelName] ?: emptyList()
                current[channelName] = (existing + sender).distinct()
                _channelUsers.value = current

                if (joinMode != EventDisplayMode.IGNORE) {
                    logEvent(channelRaw, "$sender joined $channelRaw", joinMode, MessageType.JOIN)
                }

                // Friend notification
                val user = repository?.getUser(sender, serverId)
                if (user?.isFriend == true) {
                    logSystemMessage("Status", "Your friend $sender joined $channelRaw")
                    
                    scope.launch {
                        val s = repository?.settings?.firstOrNull()
                        if (s?.soundOnFriendJoin == true) {
                            NotificationHelper.playNotificationSound(context, s)
                        }
                    }
                }

                if (sender.equals(currentNickname, ignoreCase = true)) {
                    updateChannelStatus(channelName, joined = true, banned = false)
                }
            }
            "PART" -> {
                val channelRaw = message.parameters.firstOrNull() ?: return
                val channelName = normalizeTarget(channelRaw)
                val sender = message.prefix?.substringBefore("!") ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                current[channelName] = (current[channelName] ?: emptyList()) - sender
                _channelUsers.value = current

                if (partMode != EventDisplayMode.IGNORE) {
                    logEvent(channelRaw, "$sender left $channelRaw", partMode, MessageType.PART)
                }

                if (sender.equals(currentNickname, ignoreCase = true)) {
                    updateChannelStatus(channelName, joined = false)
                }
            }
            "KICK" -> {
                val channelRaw = message.parameters.getOrNull(0) ?: return
                val channelName = normalizeTarget(channelRaw)
                val kickedUser = message.parameters.getOrNull(1) ?: return
                val reason = message.parameters.getOrNull(2) ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                current[channelName] = (current[channelName] ?: emptyList()) - kickedUser
                _channelUsers.value = current

                if (kickMode != EventDisplayMode.IGNORE) {
                    logEvent(channelRaw, "$kickedUser was kicked by ${message.prefix?.substringBefore("!")} ($reason)", kickMode, MessageType.KICK)
                }

                if (kickedUser.equals(currentNickname, ignoreCase = true)) {
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
                        if (quitMode != EventDisplayMode.IGNORE) {
                            logEvent(entry.key, "$sender quit ($reason)", quitMode, MessageType.QUIT)
                        }
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
                        if (nickChangeMode != EventDisplayMode.IGNORE) {
                            logEvent(entry.key, "$oldNick is now known as $newNick", nickChangeMode, MessageType.NICK)
                        }
                    }
                }
                _channelUsers.value = current
            }
            "MODE" -> {
                val channel = message.parameters.getOrNull(0) ?: return
                val mode = message.parameters.getOrNull(1) ?: ""
                val target = message.parameters.getOrNull(2) ?: ""
                
                // If it's a user mode change (like +i or +z), log to Status
                if (channel == currentNickname) {
                    logSystemMessage("Status", "Mode changed: $mode $target")
                    return
                }

                if (mode == "+b" && target.contains(currentNickname)) {
                    logEvent(channel, "You have been BANNED from $channel", EventDisplayMode.ROOM, MessageType.BAN)
                    updateChannelStatus(channel, joined = false, banned = true)
                    
                    scope.launch {
                        val s = repository?.settings?.firstOrNull()
                        if (s?.soundOnBan == true) {
                            NotificationHelper.playNotificationSound(context, s)
                        }
                    }
                } else if (mode.contains("b")) {
                    if (banMode != EventDisplayMode.IGNORE) {
                        logEvent(channel, "Mode change: $mode $target", banMode, MessageType.BAN)
                    }
                } else {
                    if (kickMode != EventDisplayMode.IGNORE) {
                        logEvent(channel, "Mode change: $mode $target", kickMode, MessageType.KICK)
                    }
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
            ctcp == "VERSION" -> {
                val settings = repository.settings.first()
                val version = settings?.customUserAgent ?: "IRCClient 1.0"
                send("NOTICE $sender :\u0001VERSION $version\u0001")
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
            "NOTICE" -> {
                val rawTarget = message.parameters.getOrNull(0) ?: return
                val text = message.parameters.getOrNull(1) ?: ""
                val sender = message.prefix?.substringBefore("!") ?: "system"
                
                // Route server notices to Status, but user notices to their destination
                val isServerNotice = sender.contains(".") || sender == "system" || sender == "Auth" || sender == "*"
                val finalTarget = if (isServerNotice) {
                    "Status"
                } else if (rawTarget.equals(currentNickname, ignoreCase = true)) {
                    sender
                } else {
                    normalizeTarget(rawTarget)
                }

                incrementUnreadCount(finalTarget)

                repository?.insertMessage(
                    MessageEntity(
                        serverId = serverId,
                        target = finalTarget,
                        sender = sender,
                        text = text,
                        type = MessageType.NOTICE
                    )
                )

                scope.launch {
                    val s = repository?.settings?.firstOrNull()
                    if (s?.soundOnNotice == true) {
                        NotificationHelper.playNotificationSound(context, s)
                    }
                }
            }
            "PRIVMSG" -> {
                val rawTarget = message.parameters.getOrNull(0) ?: return
                var text = message.parameters.getOrNull(1) ?: ""
                val sender = message.prefix?.substringBefore("!") ?: "system"
                val hostmask = message.prefix?.substringAfter("!", "")

                // Protocol filtering: IP_lookup or self-messages about modes
                if (sender == "IP_lookup" || (sender.equals(currentNickname, ignoreCase = true) && text.contains("mode", ignoreCase = true))) {
                    logSystemMessage("Status", "[$sender] $text")
                    return
                }

                if (hostmask != null && hostmask.isNotEmpty()) {
                    scope.launch {
                        val existing = repository.getUser(sender, serverId)
                        if (existing != null) {
                            repository.insertUser(existing.copy(hostmask = hostmask))
                        } else {
                            repository.insertUser(UserEntity(nickname = sender, serverId = serverId, hostmask = hostmask))
                        }
                    }
                }
                
                val user = repository.getUser(sender, serverId)
                if (user != null) {
                    if (user.ignoreStatus != UserStatus.NONE) return
                    if (user.silenceStatus != UserStatus.NONE) return
                }
                
                if (hostmask != null && repository.isHostmaskIgnored(serverId, hostmask)) return

                val isPrivateToMe = rawTarget.equals(currentNickname, ignoreCase = true)
                
                // Privacy check for Private Messages
                if (isPrivateToMe) {
                    val settings = repository.settings.firstOrNull()
                    if (settings?.allowPrivateOnlyFromFriends == true) {
                        val isFriend = user?.isFriend == true
                        if (!isFriend) {
                            // Auto-respond once per session/period? For now just respond.
                            if (settings.autoResponseForBlockedPv.isNotEmpty()) {
                                send("PRIVMSG $sender :${settings.autoResponseForBlockedPv}")
                            }
                            return
                        }
                    }
                }

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

                // PM Notification Sound
                if (rawTarget.equals(currentNickname, ignoreCase = true)) {
                    scope.launch {
                        val s = repository?.settings?.firstOrNull()
                        if (s?.soundOnPrivateMessage == true) {
                            NotificationHelper.playNotificationSound(context, s)
                        }
                    }
                } else {
                    // Mention Notification Sound
                    if (text.contains(currentNickname, ignoreCase = true)) {
                        scope.launch {
                            val s = repository?.settings?.firstOrNull()
                            if (s?.soundOnMention == true) {
                                NotificationHelper.playNotificationSound(context, s)
                                NotificationHelper.vibrate(context)
                            }
                        }
                    }
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
            else -> {
                if (message.command.all { it.isDigit() }) {
                    val text = message.parameters.drop(1).joinToString(" ")
                    val targetNick = message.parameters.getOrNull(1)
                    
                    val finalTarget = when (message.command) {
                        "311", "312", "317", "318", "319", "301" -> if (targetNick != null) "WHOIS $targetNick" else "Status"
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
        if (name == "Status" || name.equals(currentlyViewingTarget, ignoreCase = true)) return
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
