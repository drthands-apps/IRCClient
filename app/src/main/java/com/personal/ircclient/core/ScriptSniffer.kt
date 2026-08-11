package com.personal.ircclient.core

import android.util.Log
import com.personal.ircclient.core.model.IrcMessage
import com.personal.ircclient.data.local.entities.ScriptEntity
import com.personal.ircclient.data.repository.IrcRepository
import kotlinx.coroutines.*

/**
 * Sniffer class that intercepts all IRC messages and executes custom scripts.
 * Enhanced for v0.5.2 to handle multi-line scripts and improved variable replacement.
 */
class ScriptSniffer(private val repository: IrcRepository?) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeScripts = listOf<ScriptEntity>()

    init {
        refreshScripts()
    }

    fun refreshScripts() {
        scope.launch {
            val scripts = repository?.getActiveScripts() ?: emptyList()
            activeScripts = scripts
            Log.d("ScriptSniffer", "Scripts refreshed: ${scripts.size} active scripts loaded")
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
        
        // Execute outgoing aliases
        activeScripts.filter { it.triggerType == "ALL" || it.triggerType == "OUTGOING" }.forEach { script ->
            val lines = script.content.split("\n")
            lines.forEach { line ->
                if (line.trim().startsWith("OUT: ")) {
                    val content = line.trim().removePrefix("OUT: ").trim()
                    val parts = content.split(" -> ", limit = 2)
                    if (parts.size == 2) {
                        val alias = parts[0].trim()
                        val replacement = parts[1].trim()
                        
                        // Check if result matches alias or start with alias + space
                        val isMatch = result.equals(alias, ignoreCase = true) || 
                                     result.startsWith("$alias ", ignoreCase = true)
                        
                        if (isMatch) {
                            val args = if (result.length > alias.length) result.substring(alias.length).trim() else ""
                            
                            var finalReplacement = replacement
                            if (target != null) finalReplacement = finalReplacement.replace("%T", target)
                            if (myNick != null) finalReplacement = finalReplacement.replace("%N", myNick)
                            finalReplacement = finalReplacement.replace("%A", args)
                            
                            result = finalReplacement
                            Log.d("ScriptSniffer", "Applied script [${script.name}]: $alias -> $result")
                        }
                    }
                }
            }
        }

        return result
    }
}
