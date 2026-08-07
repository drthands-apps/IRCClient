package com.personal.ircclient.ui.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.ircclient.core.IrcManager
import com.personal.ircclient.core.security.EncryptionManager
import com.personal.ircclient.data.local.dao.TargetInfo
import com.personal.ircclient.data.local.entities.HandshakeStatus
import com.personal.ircclient.data.local.entities.MessageEntity
import com.personal.ircclient.data.local.entities.MessageType
import com.personal.ircclient.data.local.entities.UserEntity
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatsViewModel(
    private val repository: IrcRepository,
    private val ircManager: IrcManager,
    private val ttsManager: com.personal.ircclient.core.audio.TextToSpeechManager? = null
) : ViewModel() {

    var isTtsActive by mutableStateOf(false)
        private set
    
    var showEventsInRoom by mutableStateOf(true)
        private set

    fun setTtsEnabled(enabled: Boolean) {
        isTtsActive = enabled
    }
    
    fun setShowEventsInRoomEnabled(enabled: Boolean) {
        showEventsInRoom = enabled
        ircManager.setShowEventsInRoom(enabled)
    }

    init {
        // Observe all incoming messages for TTS
        viewModelScope.launch {
            ircManager.activeServers.collect { serverIds ->
                serverIds.forEach { serverId ->
                    val engine = ircManager.getEngine(serverId) ?: return@forEach
                    launch {
                        engine.messages.collect { msg ->
                            if (isTtsActive && msg.command == "PRIVMSG") {
                                val sender = msg.prefix?.substringBefore("!") ?: "Unknown"
                                val text = msg.parameters.getOrNull(1) ?: ""
                                if (!text.startsWith("[ENC]")) { // Don't read encrypted raw text
                                    ttsManager?.speak("$sender says: $text")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val activeTargets: StateFlow<List<TargetInfo>> = repository.getAllActiveTargets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allChatUsers: StateFlow<List<UserEntity>> = repository.allServers
        .flatMapLatest { servers ->
            val flows = servers.map { repository.getUsersForServer(it.id) }
            if (flows.isEmpty()) flowOf(emptyList())
            else combine(flows) { it.toList().flatten() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getChannelUsers(serverId: Long, channelName: String): StateFlow<List<String>> {
        val engine = ircManager.getEngine(serverId)
        return engine?.channelUsers?.map { it[channelName] ?: emptyList() }
            ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            ?: MutableStateFlow(emptyList())
    }

    fun getMessages(serverId: Long, target: String): Flow<List<MessageEntity>> {
        val finalTarget = if (target.startsWith("#")) target.lowercase() else target
        return repository.getMessagesForTarget(serverId, finalTarget).map { messages ->
            val user = repository.getUser(target, serverId)
            val key = user?.encryptionKey
            
            messages.map { msg ->
                val decryptedText = if (msg.isEncrypted && key != null) {
                    EncryptionManager.decrypt(msg.text, key)
                } else {
                    msg.text
                }
                
                // Parse media tag if exists
                if (decryptedText.startsWith("[MEDIA:") && decryptedText.endsWith("]")) {
                    val parts = decryptedText.removePrefix("[MEDIA:").removeSuffix("]").split(":", limit = 2)
                    if (parts.size == 2) {
                        val typeStr = parts[0]
                        val data = parts[1]
                        val type = try { MessageType.valueOf(typeStr) } catch(e: Exception) { MessageType.TEXT }
                        msg.copy(text = data, type = type)
                    } else {
                        msg.copy(text = decryptedText)
                    }
                } else {
                    msg.copy(text = decryptedText)
                }
            }
        }
    }

    fun getUser(serverId: Long, nickname: String): Flow<UserEntity?> = flow {
        emit(repository.getUser(nickname, serverId))
    }

    fun getServerName(serverId: Long): Flow<String> = repository.allServers.map { servers ->
        servers.find { it.id == serverId }?.name ?: "Server $serverId"
    }

    fun initiateSecureChat(serverId: Long, nickname: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                // Send CTCP request
                engine.send("PRIVMSG $nickname :\u0001IRC_SEC_REQ\u0001")
                // Mark locally as requested
                repository.insertUser(
                    UserEntity(
                        nickname = nickname,
                        serverId = serverId,
                        secureHandshakeStatus = HandshakeStatus.REQUESTED
                    )
                )
            }
        }
    }

    fun acceptSecureChat(serverId: Long, nickname: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                val uniqueKey = EncryptionManager.generateRandomKey()
                // Send key to the requester via CTCP
                engine.send("PRIVMSG $nickname :\u0001IRC_SEC_KEY $uniqueKey\u0001")
                // Save key locally
                repository.insertUser(
                    UserEntity(
                        nickname = nickname,
                        serverId = serverId,
                        encryptionKey = uniqueKey,
                        secureHandshakeStatus = HandshakeStatus.COMPLETED
                    )
                )
            }
        }
    }

    fun sendMessage(serverId: Long, target: String, text: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                if (text.startsWith("/")) {
                    val command = text.substring(1)
                    engine.send(command)
                    return@launch
                }
                
                if (target == "Status") {
                    engine.send(text)
                    return@launch
                }

                val user = repository.getUser(target, serverId)
                val key = user?.encryptionKey
                
                val finalMessage = if (key != null) {
                    "[ENC] " + EncryptionManager.encrypt(text, key)
                } else {
                    text
                }
                
                engine.send("PRIVMSG $target :$finalMessage")
                
                // Normalize target
                val finalTarget = if (target.startsWith("#")) target.lowercase() else target

                repository.insertMessage(
                    MessageEntity(
                        serverId = serverId,
                        target = finalTarget,
                        sender = "me", 
                        text = if (key != null) EncryptionManager.encrypt(text, key) else text,
                        isEncrypted = key != null,
                        type = MessageType.TEXT
                    )
                )
            }
        }
    }

    fun sendMedia(serverId: Long, target: String, type: MessageType, data: String) {
        viewModelScope.launch {
            val engine = ircManager.getEngine(serverId)
            if (engine != null) {
                val user = repository.getUser(target, serverId)
                val key = user?.encryptionKey
                
                val mediaTag = "[MEDIA:${type.name}:$data]"
                val finalMessage = if (key != null) {
                    "[ENC] " + EncryptionManager.encrypt(mediaTag, key)
                } else {
                    mediaTag
                }
                
                engine.send("PRIVMSG $target :$finalMessage")
                
                // Normalize target
                val finalTarget = if (target.startsWith("#")) target.lowercase() else target

                repository.insertMessage(
                    MessageEntity(
                        serverId = serverId,
                        target = finalTarget,
                        sender = "me",
                        text = if (key != null) EncryptionManager.encrypt(mediaTag, key) else mediaTag,
                        isEncrypted = key != null,
                        type = type
                    )
                )
            }
        }
    }
}
