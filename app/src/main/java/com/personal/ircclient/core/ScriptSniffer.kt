package com.personal.ircclient.core

import android.util.Log
import com.personal.ircclient.core.model.IrcMessage
import com.personal.ircclient.data.local.entities.ScriptEntity
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.*

/**
 * Sniffer class that intercepts all IRC messages and executes custom scripts.
 */
class ScriptSniffer(private val repository: IrcRepository?) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeScripts = listOf<ScriptEntity>()

    init {
        refreshScripts()
    }

    fun refreshScripts() {
        scope.launch {
            activeScripts = repository?.getActiveScripts() ?: emptyList()
        }
    }

    /**
     * Intercepts an incoming message from the server.
     */
    fun onIncomingMessage(message: IrcMessage): IrcMessage? {
        // TODO: Implement incoming transformations (e.g., custom highlighting or auto-ignore)
        return message
    }

    /**
     * Intercepts an outgoing message from the user.
     * Support variables: 
     * %T (Target), %N (My Nick), %A (Arguments)
     */
    fun onOutgoingMessage(raw: String, target: String? = null, myNick: String? = null): String? {
        var result = raw
        
        activeScripts.filter { it.triggerType == "ALL" || it.triggerType == "OUTGOING" }.forEach { script ->
            // Syntax: OUT: /alias -> COMMAND
            val lines = script.content.split("\n")
            lines.forEach { line ->
                if (line.startsWith("OUT: ")) {
                    val parts = line.removePrefix("OUT: ").split(" -> ", limit = 2)
                    if (parts.size == 2) {
                        val alias = parts[0].trim()
                        var replacement = parts[1].trim()
                        
                        if (result.startsWith(alias, ignoreCase = true)) {
                            val args = result.removePrefix(alias).trim()
                            
                            // Replace variables
                            if (target != null) replacement = replacement.replace("%T", target)
                            if (myNick != null) replacement = replacement.replace("%N", myNick)
                            replacement = replacement.replace("%A", args)
                            
                            result = replacement
                            Log.d("ScriptSniffer", "Script applied: $alias -> $result")
                        }
                    }
                }
            }
        }

        return result
    }
}
