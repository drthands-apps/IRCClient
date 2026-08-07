package com.personal.ircclient.core

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
    var showEventsInRoom: Boolean = true
    private var currentNickname: String = config.nickname

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    
    private val _messages = MutableSharedFlow<IrcMessage>()
    val messages: SharedFlow<IrcMessage> = _messages.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _channelUsers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val channelUsers: StateFlow<Map<String, List<String>>> = _channelUsers.asStateFlow()

    private var job: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastMessageTime = System.currentTimeMillis()
    private val motdBuffer = StringBuilder()

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

    private suspend fun logEvent(channel: String?, text: String) {
        val target = if (showEventsInRoom && channel != null) channel else "Status"
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
                    
                    // PASS THROUGH SNIFFER
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
                        isSystemMessage = true
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
            "353" -> { // RPL_NAMREPLY
                val channel = message.parameters.getOrNull(2) ?: return
                val users = message.parameters.getOrNull(3)?.split(" ")?.filter { it.isNotEmpty() } ?: return
                
                val current = _channelUsers.value.toMutableMap()
                val existing = current[channel] ?: emptyList()
                current[channel] = (existing + users).distinct()
                _channelUsers.value = current
            }
            "366" -> { // RPL_ENDOFNAMES
                // Names list complete for a channel
            }
            "JOIN" -> {
                val channelName = message.parameters.firstOrNull() ?: return
                val sender = message.prefix?.substringBefore("!") ?: ""
                
                // Update user list
                val current = _channelUsers.value.toMutableMap()
                val existing = current[channelName] ?: emptyList()
                current[channelName] = (existing + sender).distinct()
                _channelUsers.value = current

                logEvent(channelName, "$sender joined $channelName")

                if (sender == config.nickname) {
                    repository?.insertChannel(
                        ChannelEntity(
                            serverId = serverId,
                            name = channelName,
                            isJoined = true
                        )
                    )
                }
            }
            "PART" -> {
                val channelName = message.parameters.firstOrNull() ?: return
                val sender = message.prefix?.substringBefore("!") ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                current[channelName] = (current[channelName] ?: emptyList()) - sender
                _channelUsers.value = current

                logEvent(channelName, "$sender left $channelName")

                if (sender == config.nickname) {
                    repository?.insertChannel(
                        ChannelEntity(
                            serverId = serverId,
                            name = channelName,
                            isJoined = false
                        )
                    )
                }
            }
            "KICK" -> {
                val channelName = message.parameters.getOrNull(0) ?: return
                val kickedUser = message.parameters.getOrNull(1) ?: return
                val reason = message.parameters.getOrNull(2) ?: ""
                
                val current = _channelUsers.value.toMutableMap()
                current[channelName] = (current[channelName] ?: emptyList()) - kickedUser
                _channelUsers.value = current

                logEvent(channelName, "$kickedUser was kicked by ${message.prefix?.substringBefore("!")} ($reason)")

                if (kickedUser == config.nickname) {
                    repository?.insertChannel(
                        ChannelEntity(
                            serverId = serverId,
                            name = channelName,
                            isJoined = false
                        )
                    )
                }
            }
            "QUIT" -> {
                val sender = message.prefix?.substringBefore("!") ?: ""
                val reason = message.parameters.firstOrNull() ?: ""
                val current = _channelUsers.value.toMutableMap()
                for (entry in current) {
                    if (entry.value.contains(sender)) {
                        current[entry.key] = entry.value - sender
                        logEvent(entry.key, "$sender quit ($reason)")
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
                        logEvent(entry.key, "$oldNick is now known as $newNick")
                    }
                }
                _channelUsers.value = current
            }
        }
    }

    private suspend fun handleCtcp(sender: String, ctcp: String) {
        if (repository == null) return
        
        when {
            ctcp == "IRC_SEC_REQ" -> {
                // Someone wants to start a secure chat with us
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
                // Someone accepted our request and sent a key
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
                
                // Handle CTCP (handshake for secure chat)
                if (text.startsWith("\u0001") && text.endsWith("\u0001")) {
                    val ctcp = text.substring(1, text.length - 1)
                    handleCtcp(sender, ctcp)
                    return
                }

                val isEncrypted = text.startsWith("[ENC] ")
                if (isEncrypted) {
                    text = text.removePrefix("[ENC] ").trim()
                }

                // Normalize target: if it's a channel, lowercase it
                val finalTarget = if (rawTarget.startsWith("#")) {
                    rawTarget.lowercase()
                } else if (rawTarget.equals(currentNickname, ignoreCase = true)) {
                    sender
                } else {
                    rawTarget
                }

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
                
                val finalTarget = if (target == config.nickname || target == "*") "Status" else target

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
                // Save numeric replies to Status or specific window
                if (message.command.all { it.isDigit() }) {
                    val text = message.parameters.drop(1).joinToString(" ")
                    
                    // Special case: WHOIS info usually has nick as first parameter after our own nick
                    // Format: <our_nick> <target_nick> <info...>
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

    suspend fun sendMessage(message: IrcMessage) {
        send(message.build())
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
