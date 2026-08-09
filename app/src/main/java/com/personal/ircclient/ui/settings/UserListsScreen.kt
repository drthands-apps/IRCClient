package com.personal.ircclient.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.ircclient.core.utils.Localizer
import com.personal.ircclient.data.local.entities.UserEntity
import com.personal.ircclient.data.local.entities.UserStatus
import com.personal.ircclient.ui.chats.ChatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListsScreen(
    viewModel: ChatsViewModel,
    onBack: () -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    var selectedServerId by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(servers) {
        if (selectedServerId == null && servers.isNotEmpty()) {
            selectedServerId = servers[0].id
        }
    }

    val users by viewModel.allChatUsers.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Localizer.getString("friends", lang),
        Localizer.getString("ignored", lang),
        Localizer.getString("silenced", lang)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localizer.getString("user_management", lang)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (servers.size > 1) {
                var expanded by remember { mutableStateOf(false) }
                val selectedServer = servers.find { it.id == selectedServerId }
                
                Box(modifier = Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(selectedServer?.name ?: Localizer.getString("select_server", lang))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        servers.forEach { server ->
                            DropdownMenuItem(
                                text = { Text(server.name) },
                                onClick = {
                                    selectedServerId = server.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val serverUsers = users.filter { it.serverId == selectedServerId }

            when (selectedTab) {
                0 -> FriendList(serverUsers, lang, viewModel)
                1 -> UserList(serverUsers.filter { it.ignoreStatus != UserStatus.NONE }, lang, viewModel, isIgnore = true)
                2 -> UserList(serverUsers.filter { it.silenceStatus != UserStatus.NONE }, lang, viewModel, isIgnore = false)
            }
        }
    }
}

@Composable
fun FriendList(users: List<UserEntity>, lang: String, viewModel: ChatsViewModel) {
    val friends = users.filter { it.isFriend }
    LazyColumn {
        items(friends) { friend ->
            ListItem(
                headlineContent = { Text(friend.nickname) },
                leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700)) },
                trailingContent = {
                    IconButton(onClick = { viewModel.setFriend(friend.serverId, friend.nickname, false) }) {
                        Icon(Icons.Default.Delete, contentDescription = Localizer.getString("remove", lang))
                    }
                }
            )
        }
    }
}

@Composable
fun UserList(users: List<UserEntity>, lang: String, viewModel: ChatsViewModel, isIgnore: Boolean) {
    LazyColumn {
        items(users) { user ->
            val status = if (isIgnore) user.ignoreStatus else user.silenceStatus
            ListItem(
                headlineContent = { Text(user.nickname) },
                supportingContent = { Text("${Localizer.getString("mode", lang)}: ${status.name}") },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { 
                        if (isIgnore) viewModel.ignoreUser(user.serverId, user.nickname, UserStatus.NONE)
                        else viewModel.silenceUser(user.serverId, user.nickname, UserStatus.NONE)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = Localizer.getString("remove", lang))
                    }
                }
            )
        }
    }
}
