package com.personal.ircclient.core.commands

import com.personal.ircclient.core.IrcEngine
import com.personal.ircclient.data.local.entities.UserEntity
import com.personal.ircclient.data.local.entities.UserStatus
import com.personal.ircclient.data.repository.IrcRepository

import kotlinx.coroutines.flow.firstOrNull

class CommandHandler(private val engine: IrcEngine, private val repository: IrcRepository? = null) {

    suspend fun handleCommand(target: String, input: String): Boolean {
        if (!input.startsWith("/")) return false

        val parts = input.substring(1).split(" ", limit = 2)
        val command = parts[0].uppercase()
        val params = parts.getOrNull(1) ?: ""

        return when (command) {
            "JOIN" -> { join(params); true }
            "PART" -> { part(target, params); true }
            "QUIT" -> { quit(params); true }
            "NICK" -> { nick(params); true }
            "MSG", "QUERY" -> { msg(params); true }
            "ME" -> { me(target, params); true }
            "TOPIC" -> { topic(target, params); true }
            "WHOIS" -> { whois(params); true }
            "MODE" -> { mode(target, params); true }
            "KICK" -> { kick(target, params); true }
            "BAN" -> { ban(target, params); true }
            "UNBAN" -> { unban(target, params); true }
            "LIST" -> { list(params); true }
            "NAMES" -> { names(target, params); true }
            "INVITE" -> { invite(params); true }
            "AWAY" -> { away(params); true }
            "BACK" -> { back(); true }
            "NOTICE" -> { notice(params); true }
            "CTCP" -> { ctcp(params); true }
            "NOTIFY" -> { notify(params); true }
            "IGNORE" -> { ignore(params, UserStatus.DEFINITIVE); true }
            "TIGNORE" -> { ignore(params, UserStatus.TEMPORAL); true }
            "UNIGNORE" -> { ignore(params, UserStatus.NONE); true }
            "SILENCE" -> { silence(params, UserStatus.DEFINITIVE); true }
            "TSILENCE" -> { silence(params, UserStatus.TEMPORAL); true }
            "UNSILENCE" -> { silence(params, UserStatus.NONE); true }
            "FRIEND" -> { friend(params, true); true }
            "UNFRIEND" -> { friend(params, false); true }
            "CLEAR" -> { clear(target); true }
            "OP" -> { op(target, params, true); true }
            "DEOP" -> { op(target, params, false); true }
            "VOICE" -> { voice(target, params, true); true }
            "DEVOICE" -> { voice(target, params, false); true }
            "KICKBAN" -> { kickban(target, params); true }
            else -> false
        }
    }

    private suspend fun kickban(target: String, params: String) {
        val nick = params.substringBefore(" ")
        val reason = params.substringAfter(" ", "")
        if (nick.isNotEmpty()) {
            engine.send("MODE $target +b $nick")
            engine.send("KICK $target $nick${if (reason.isNotEmpty()) " :$reason" else ""}")
        }
    }

    private suspend fun op(target: String, nick: String, active: Boolean) {
        if (nick.isBlank()) return
        engine.send("MODE $target ${if (active) "+o" else "-o"} $nick")
    }

    private suspend fun voice(target: String, nick: String, active: Boolean) {
        if (nick.isBlank()) return
        engine.send("MODE $target ${if (active) "+v" else "-v"} $nick")
    }

    private suspend fun silence(nick: String, status: UserStatus) {
        if (nick.isBlank()) return
        if (status != UserStatus.NONE) {
            engine.send("SILENCE +$nick")
        } else {
            engine.send("SILENCE -$nick")
        }
        
        if (repository != null) {
            val user = repository.getUser(nick, engine.serverId)
            if (user != null) {
                repository.insertUser(user.copy(silenceStatus = status))
            } else {
                repository.insertUser(UserEntity(nickname = nick, serverId = engine.serverId, silenceStatus = status))
            }
        }
    }

    private suspend fun notify(params: String) {
        if (params.isBlank()) return
        engine.send("NOTIFY $params")
    }

    private suspend fun friend(nick: String, active: Boolean) {
        if (nick.isBlank() || repository == null) return
        val user = repository.getUser(nick, engine.serverId)
        if (user != null) {
            repository.insertUser(user.copy(isFriend = active))
        } else {
            repository.insertUser(UserEntity(nickname = nick, serverId = engine.serverId, isFriend = active))
        }
    }
    
    fun getAvailableCommands(target: String, isOp: Boolean): List<String> {
        val isChannel = target.startsWith("#")
        val isStatus = target == "Status"
        
        return mutableListOf<String>().apply {
            add("NICK")
            add("AWAY")
            add("MSG")
            add("QUIT")
            
            if (isStatus) {
                add("JOIN")
                add("LIST")
                add("WHOIS")
            } else if (isChannel) {
                add("ME")
                add("PART")
                add("NAMES")
                add("TOPIC")
                add("LIST")
                if (isOp) {
                    add("KICK")
                    add("BAN")
                    add("KICKBAN")
                    add("OP")
                    add("DEOP")
                    add("VOICE")
                    add("DEVOICE")
                    add("MODE")
                    add("INVITE")
                }
            } else { // Private
                add("ME")
                add("WHOIS")
                add("QUERY")
                add("NOTICE")
                add("IGNORE")
                add("TIGNORE")
                add("SILENCE")
                add("TSILENCE")
                add("FRIEND")
            }
            add("CLEAR")
        }
    }

    private suspend fun clear(target: String) {
        if (repository == null) return
        repository.clearHistory(engine.serverId, target)
    }

    private suspend fun ignore(nick: String, status: UserStatus) {
        if (nick.isBlank() || repository == null) return
        val user = repository.getUser(nick, engine.serverId)
        if (user != null) {
            repository.insertUser(user.copy(ignoreStatus = status))
        } else {
            repository.insertUser(UserEntity(nickname = nick, serverId = engine.serverId, ignoreStatus = status))
        }
    }

    private suspend fun join(params: String) {
        if (params.isBlank()) return
        engine.send("JOIN $params")
    }

    private suspend fun part(target: String, params: String) {
        val channel = if (params.startsWith("#")) params.substringBefore(" ") else target
        val reason = if (params.startsWith("#") && params.contains(" ")) params.substringAfter(" ") else params
        if (channel.startsWith("#")) {
            engine.send("PART $channel${if (reason.isNotEmpty()) " :$reason" else ""}")
        }
    }

    private suspend fun quit(reason: String) {
        engine.send("QUIT${if (reason.isNotEmpty()) " :$reason" else ""}")
    }

    private suspend fun nick(newNick: String) {
        if (newNick.isBlank()) return
        engine.send("NICK $newNick")
    }

    private suspend fun msg(params: String) {
        val target = params.substringBefore(" ")
        val text = params.substringAfter(" ", "")
        if (target.isNotEmpty() && text.isNotEmpty()) {
            engine.send("PRIVMSG $target :$text")
        }
    }

    private suspend fun me(target: String, params: String) {
        if (params.isBlank()) return
        engine.send("PRIVMSG $target :\u0001ACTION $params\u0001")
    }

    private suspend fun topic(target: String, params: String) {
        val channel = if (params.startsWith("#")) params.substringBefore(" ") else target
        val topic = if (params.startsWith("#") && params.contains(" ")) params.substringAfter(" ") else params
        if (channel.startsWith("#")) {
            engine.send("TOPIC $channel${if (topic.isNotEmpty()) " :$topic" else ""}")
        }
    }

    private suspend fun whois(nick: String) {
        if (nick.isBlank()) return
        engine.send("WHOIS $nick")
    }

    private suspend fun mode(target: String, params: String) {
        val dest = if (params.startsWith("#") || params.contains(" ")) params.substringBefore(" ") else target
        val modes = if (params.contains(" ")) params.substringAfter(" ") else params
        engine.send("MODE $dest $modes")
    }

    private suspend fun kick(target: String, params: String) {
        val channel = if (params.startsWith("#")) params.substringBefore(" ") else target
        val rest = if (params.startsWith("#")) params.substringAfter(" ") else params
        val nick = rest.substringBefore(" ")
        val reason = rest.substringAfter(" ", "")
        if (channel.startsWith("#") && nick.isNotEmpty()) {
            engine.send("KICK $channel $nick${if (reason.isNotEmpty()) " :$reason" else ""}")
        }
    }

    private suspend fun ban(target: String, params: String) {
        val channel = if (params.startsWith("#")) params.substringBefore(" ") else target
        val mask = if (params.startsWith("#")) params.substringAfter(" ") else params
        if (channel.startsWith("#") && mask.isNotEmpty()) {
            engine.send("MODE $channel +b $mask")
        }
    }

    private suspend fun unban(target: String, params: String) {
        val channel = if (params.startsWith("#")) params.substringBefore(" ") else target
        val mask = if (params.startsWith("#")) params.substringAfter(" ") else params
        if (channel.startsWith("#") && mask.isNotEmpty()) {
            engine.send("MODE $channel -b $mask")
        }
    }

    private suspend fun list(params: String) {
        engine.send("LIST $params")
    }

    private suspend fun names(target: String, params: String) {
        val channel = params.ifBlank { target }
        engine.send("NAMES $channel")
    }

    private suspend fun invite(params: String) {
        val nick = params.substringBefore(" ")
        val channel = params.substringAfter(" ", "")
        if (nick.isNotEmpty() && channel.isNotEmpty()) {
            engine.send("INVITE $nick $channel")
        }
    }

    private suspend fun away(message: String) {
        val settings = repository?.settings?.firstOrNull()
        val finalMsg = message.ifEmpty { settings?.defaultAwayMessage ?: "I am away" }
        engine.send("AWAY :$finalMsg")
        if (repository != null) {
            engine.logSystemMessage("Status", "You are now marked as away: $finalMsg")
        }
    }

    private suspend fun back() {
        val settings = repository?.settings?.firstOrNull()
        val finalMsg = settings?.defaultBackMessage ?: "I am back"
        engine.send("AWAY") 
        engine.logSystemMessage("Status", "You are no longer marked as away. ($finalMsg)")
    }

    private suspend fun notice(params: String) {
        val dest = params.substringBefore(" ")
        val text = params.substringAfter(" ", "")
        if (dest.isNotEmpty() && text.isNotEmpty()) {
            engine.send("NOTICE $dest :$text")
            
            val isChannel = dest.startsWith("#") || dest.startsWith("&") || dest.startsWith("+") || dest.startsWith("!")
            val finalTarget = if (isChannel) dest.lowercase() else dest

            // Log locally so sender can see it
            repository?.insertMessage(
                com.personal.ircclient.data.local.entities.MessageEntity(
                    serverId = engine.serverId,
                    target = finalTarget,
                    sender = "me",
                    text = text,
                    type = com.personal.ircclient.data.local.entities.MessageType.NOTICE
                )
            )
        }
    }

    private suspend fun ctcp(params: String) {
        val dest = params.substringBefore(" ")
        val cmd = params.substringAfter(" ", "")
        if (dest.isNotEmpty() && cmd.isNotEmpty()) {
            engine.send("PRIVMSG $dest :\u0001${cmd.uppercase()}\u0001")
        }
    }
}
