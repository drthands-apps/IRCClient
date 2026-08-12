package com.personal.ircclient.core.model

data class IrcConfig(
    val host: String,
    val port: Int = 6667,
    val nickname: String,
    val altNickname: String? = null,
    val generateRandomNick: Boolean = false,
    val username: String = "irc_user",
    val realName: String = "IRC Client User",
    val password: String? = null,
    val useSsl: Boolean = false,
    val allowPlainText: Boolean = true,
    val encoding: String = "UTF-8",
    
    // SASL
    val useSasl: Boolean = false,
    val saslUsername: String? = null,
    val saslPassword: String? = null,
    
    // Bouncer
    val useBouncer: Boolean = false,
    val bouncerNetwork: String? = null,
    
    // Extensions
    val email: String? = null,
    val onConnectCommands: String? = null
)
