package com.personal.ircclient.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Servers : Screen("servers", "Servers", Icons.Default.Dns)
    object AddServer : Screen("add_server", "Add Server", Icons.Default.Add)
    object EditServer : Screen("edit_server/{serverId}", "Edit Server", Icons.Default.Add) {
        fun createRoute(serverId: Long) = "edit_server/$serverId"
    }
    object Channels : Screen("channels", "Rooms", Icons.Default.Chat)
    object DirectMessages : Screen("direct_messages", "Privates", Icons.Default.People)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object UserLists : Screen("user_lists", "User Lists", Icons.Default.People)
    object ChannelDiscovery : Screen("discovery/{serverId}", "Discovery", Icons.Default.Search) {
        fun createRoute(serverId: Long) = "discovery/$serverId"
    }
    object ChatDetail : Screen("chat_detail/{serverId}/{target}", "Chat", Icons.Default.Chat) {
        fun createRoute(serverId: Long, target: String): String {
            val encodedTarget = java.net.URLEncoder.encode(target, "UTF-8")
            return "chat_detail/$serverId/$encodedTarget"
        }
    }
}
