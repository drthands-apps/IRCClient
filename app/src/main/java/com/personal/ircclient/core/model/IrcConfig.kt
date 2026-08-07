package com.personal.ircclient.core.model

data class IrcConfig(
    val host: String,
    val port: Int = 6667,
    val nickname: String,
    val username: String = "irc_user",
    val realName: String = "IRC Client User",
    val password: String? = null,
    val useSsl: Boolean = false,
    val encoding: String = "UTF-8"
)
