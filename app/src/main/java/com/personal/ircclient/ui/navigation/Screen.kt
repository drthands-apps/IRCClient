package com.personal.ircclient.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Servers : Screen("servers", "Servers", Icons.Default.Dns)
    object AddServer : Screen("add_server", "Add Server", Icons.Default.Add)
    object Chats : Screen("chats", "Chats", Icons.Default.Chat)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object ChannelDiscovery : Screen("discovery/{serverId}", "Discovery", Icons.Default.Search) {
        fun createRoute(serverId: Long) = "discovery/$serverId"
    }
    object ChatDetail : Screen("chat_detail/{serverId}/{target}", "Chat", Icons.Default.Chat) {
        fun createRoute(serverId: Long, target: String) = "chat_detail/$serverId/$target"
    }
}
