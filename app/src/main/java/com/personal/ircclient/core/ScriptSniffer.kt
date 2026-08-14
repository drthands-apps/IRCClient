package com.personal.ircclient.core

import android.util.Log
import com.personal.ircclient.BuildConfig
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
        if (BuildConfig.FLAVOR == "lite") {
            activeScripts = emptyList()
            return
        }
        scope.launch {
            val scripts = repository?.getActiveScripts() ?: emptyList()
            activeScripts = scripts
            Log.i("ScriptSniffer", "Scripts refreshed: ${scripts.size} active scripts loaded")
            scripts.forEach { Log.i("ScriptSniffer", " - Active script: [${it.name}] content: ${it.content.take(20)}...") }
        }
    }

    /**
     * Intercepts an incoming message from the server.
     */
    fun onIncomingMessage(message: IrcMessage): IrcMessage? {
        if (BuildConfig.FLAVOR == "lite") return message
        
        val text = message.parameters.getOrNull(1) ?: return message
        var currentText = text
        var modified = false

        activeScripts.filter { it.isActive && (it.triggerType == "ALL" || it.triggerType == "INCOMING") }.forEach { script ->
            val lines = script.content.split("\n")
            lines.forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("IN: ")) {
                    val content = trimmedLine.removePrefix("IN: ").trim()
                    val parts = content.split(" -> ", limit = 2)
                    if (parts.size == 2) {
                        val word = parts[0].trim()
                        val replacement = parts[1].trim()
                        
                        if (currentText.contains(word, ignoreCase = true)) {
                            currentText = currentText.replace(word, replacement, ignoreCase = true)
                            modified = true
                        }
                    }
                }
            }
        }

        return if (modified) {
            val newParams = message.parameters.toMutableList()
            if (newParams.size > 1) newParams[1] = currentText
            message.copy(parameters = newParams, isModifiedByScript = true)
        } else message
    }

    /**
     * Intercepts an outgoing message from the user.
     * Support variables: 
     * %T (Target), %N (My Nick), %A (Arguments)
     */
    fun onOutgoingMessage(raw: String, target: String? = null, myNick: String? = null): String? {
        if (BuildConfig.FLAVOR == "lite") return raw

        var result = raw
        val normalizedInput = raw.trim()
        Log.i("ScriptSniffer", "Processing outgoing: '$normalizedInput' (Target: $target, Active Scripts: ${activeScripts.size})")
        
        // Execute outgoing aliases
        activeScripts.filter { it.isActive && (it.triggerType == "ALL" || it.triggerType == "OUTGOING") }.forEach { script ->
            val lines = script.content.split("\n")
            lines.forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("OUT: ")) {
                    val content = trimmedLine.removePrefix("OUT: ").trim()
                    val parts = content.split(" -> ", limit = 2)
                    if (parts.size == 2) {
                        val alias = parts[0].trim()
                        val replacement = parts[1].trim()
                        
                        // Flexible matching: check for /, !, . or no prefix
                        val cleanAlias = alias.removePrefix("/").removePrefix("!").removePrefix(".")
                        val cleanInput = normalizedInput.removePrefix("/").removePrefix("!").removePrefix(".")
                        
                        val isMatch = cleanInput.equals(cleanAlias, ignoreCase = true) || 
                                     cleanInput.startsWith("$cleanAlias ", ignoreCase = true)
                        
                        if (isMatch) {
                            val args = if (cleanInput.length > cleanAlias.length) cleanInput.substring(cleanAlias.length).trim() else ""
                            
                            var finalReplacement = replacement
                            if (target != null) finalReplacement = finalReplacement.replace("%T", target)
                            if (myNick != null) finalReplacement = finalReplacement.replace("%N", myNick)
                            finalReplacement = finalReplacement.replace("%A", args)
                            
                            result = finalReplacement
                            Log.i("ScriptSniffer", "SUCCESS! Script [${script.name}] matched alias '$alias'. Replacement: '$result'")
                        }
                    }
                }
            }
        }

        return result
    }
}
