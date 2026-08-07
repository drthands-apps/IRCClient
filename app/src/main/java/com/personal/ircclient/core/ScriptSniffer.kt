package com.personal.ircclient.core

import android.util.Log
import com.personal.ircclient.core.model.IrcMessage

/**
 * Sniffer class that intercepts all IRC messages.
 * This class acts as a middleware between the raw socket and the application.
 */
class ScriptSniffer {
    
    /**
     * Intercepts an incoming message from the server.
     * Scripts can modify the message or return null to drop it.
     * Scripts that modify one message will mark that message as modified by script
     */
    fun onIncomingMessage(message: IrcMessage): IrcMessage? {
        Log.d("IrcSniffer", "IN: ${message.raw}")
        
        // Logic to mark message as modified if scripts change it
        // message.isModifiedByScript = true
        
        return message
    }

    /**
     * Intercepts an outgoing message from the user.
     * Scripts can transform the command before it reaches the server.
     */
    fun onOutgoingMessage(raw: String): String? {
        Log.d("IrcSniffer", "OUT: $raw")
        
        // Example: Auto-replace shortcuts
        // if (raw == "/hi") return "PRIVMSG #channel :Hello everyone!"

        return raw
    }
}
