package com.personal.ircclient.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.ircclient.IrcApplication
import com.personal.ircclient.core.utils.Localizer
import com.personal.ircclient.ui.chats.ChatDetailScreen
import com.personal.ircclient.ui.chats.ChatsScreen
import com.personal.ircclient.ui.chats.ChatsViewModel
import com.personal.ircclient.ui.discovery.ChannelListScreen
import com.personal.ircclient.ui.navigation.Screen
import com.personal.ircclient.ui.servers.ServerEditScreen
import com.personal.ircclient.ui.servers.ServersScreen
import com.personal.ircclient.ui.servers.ServersViewModel
import com.personal.ircclient.ui.settings.SettingsScreen
import com.personal.ircclient.ui.settings.UserListsScreen
import com.personal.ircclient.ui.theme.IRCClientTheme

@Suppress("UNCHECKED_CAST")
class ServersViewModelFactory(private val app: IrcApplication) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ServersViewModel(app.repository, app.ircManager) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as IrcApplication
    
    val serversViewModel: ServersViewModel = viewModel(
        factory = ServersViewModelFactory(app)
    )
    
    val chatsViewModel: ChatsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatsViewModel(app.repository, app.ircManager, app.ttsManager) as T
            }
        }
    )

    val settings by chatsViewModel.settingsState.collectAsState()
    val lang = settings.language

    IRCClientTheme(themeName = settings.themeName) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val items = listOf(
            Screen.Servers,
            Screen.Channels,
            Screen.DirectMessages,
            Screen.Settings
        )

        val channelsUnread by chatsViewModel.channelsUnreadCount.collectAsState()
        val privatesUnread by chatsViewModel.privatesUnreadCount.collectAsState()

        var showJoinChannelDialog by remember { mutableStateOf<Long?>(null) } // serverId
        var showNewPrivateDialog by remember { mutableStateOf<Long?>(null) } // serverId

        if (showJoinChannelDialog != null) {
            var channelName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showJoinChannelDialog = null },
                title = { Text(Localizer.getString("join_room", lang)) },
                text = {
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        label = { Text(Localizer.getString("nav_rooms", lang)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (channelName.isNotBlank()) {
                            val formatted = if (channelName.startsWith("#")) channelName else "#$channelName"
                            serversViewModel.joinChannel(showJoinChannelDialog!!, formatted)
                            navController.navigate(Screen.ChatDetail.createRoute(showJoinChannelDialog!!, formatted))
                            showJoinChannelDialog = null
                        }
                    }) { Text(Localizer.getString("save", lang)) }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinChannelDialog = null }) { Text(Localizer.getString("cancel", lang)) }
                }
            )
        }

        if (showNewPrivateDialog != null) {
            var nickname by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewPrivateDialog = null },
                title = { Text(Localizer.getString("new_private", lang)) },
                text = {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Nickname") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (nickname.isNotBlank()) {
                            navController.navigate(Screen.ChatDetail.createRoute(showNewPrivateDialog!!, nickname))
                            showNewPrivateDialog = null
                        }
                    }) { Text(Localizer.getString("save", lang)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewPrivateDialog = null }) { Text(Localizer.getString("cancel", lang)) }
                }
            )
        }

        Scaffold(
            topBar = {
                if (currentRoute != null && !currentRoute.startsWith("chat_detail")) {
                    val title = when (currentRoute) {
                        Screen.Servers.route -> Localizer.getString("nav_servers", lang)
                        Screen.Channels.route -> Localizer.getString("nav_rooms", lang)
                        Screen.DirectMessages.route -> Localizer.getString("nav_privates", lang)
                        Screen.Settings.route -> Localizer.getString("nav_settings", lang)
                        else -> "FenixIRC"
                    }
                    
                    TopAppBar(
                        title = { Text(title) },
                        actions = {
                            when (currentRoute) {
                                Screen.Channels.route -> {
                                    var showMenu by remember { mutableStateOf(false) }
                                    val activeServers by serversViewModel.activeServerIds.collectAsState()
                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Default.AddCircle, contentDescription = null)
                                        }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            activeServers.forEach { serverId ->
                                                val serverName by chatsViewModel.getServerName(serverId).collectAsState(initial = "Server $serverId")
                                                DropdownMenuItem(
                                                    text = { Text("${Localizer.getString("join_room", lang)} ($serverName)") },
                                                    onClick = { showMenu = false; showJoinChannelDialog = serverId }
                                                )
                                            }
                                            HorizontalDivider()
                                            activeServers.forEach { serverId ->
                                                val serverName by chatsViewModel.getServerName(serverId).collectAsState(initial = "Server $serverId")
                                                DropdownMenuItem(
                                                    text = { Text("${Localizer.getString("discover", lang)} ($serverName)") },
                                                    onClick = { showMenu = false; navController.navigate(Screen.ChannelDiscovery.createRoute(serverId)) }
                                                )
                                            }
                                        }
                                    }
                                }
                                Screen.DirectMessages.route -> {
                                    var showMenu by remember { mutableStateOf(false) }
                                    val activeServers by serversViewModel.activeServerIds.collectAsState()
                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                                        }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            DropdownMenuItem(
                                                text = { Text(Localizer.getString("friend_list", lang)) },
                                                onClick = { showMenu = false; navController.navigate(Screen.UserLists.route) }
                                            )
                                            HorizontalDivider()
                                            activeServers.forEach { serverId ->
                                                val serverName by chatsViewModel.getServerName(serverId).collectAsState(initial = "Server $serverId")
                                                DropdownMenuItem(
                                                    text = { Text("${Localizer.getString("search_user", lang)} ($serverName)") },
                                                    onClick = { showMenu = false; showNewPrivateDialog = serverId }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { 
                                val badgeCount = when (screen) {
                                    Screen.Channels -> channelsUnread
                                    Screen.DirectMessages -> privatesUnread
                                    else -> 0
                                }
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) {
                                            Badge { Text(badgeCount.toString()) }
                                        }
                                    }
                                ) {
                                    val icon = when (screen) {
                                        Screen.Servers -> Icons.Default.Dns
                                        Screen.Channels -> Icons.Default.ChatBubble
                                        Screen.DirectMessages -> Icons.Default.Person
                                        Screen.Settings -> Icons.Default.Settings
                                        else -> screen.icon
                                    }
                                    Icon(icon, contentDescription = null)
                                }
                            },
                            label = { 
                                val label = when (screen) {
                                    Screen.Servers -> Localizer.getString("nav_servers", lang)
                                    Screen.Channels -> Localizer.getString("nav_rooms", lang)
                                    Screen.DirectMessages -> Localizer.getString("nav_privates", lang)
                                    Screen.Settings -> Localizer.getString("nav_settings", lang)
                                    else -> screen.title
                                }
                                Text(label) 
                            },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            },
            floatingActionButton = {
                when (currentRoute) {
                    Screen.Servers.route -> {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            FloatingActionButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.Dns, contentDescription = null)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(Localizer.getString("add_new", lang)) },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    onClick = { 
                                        showMenu = false
                                        navController.navigate(Screen.AddServer.route) 
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(Localizer.getString("connect_all", lang)) },
                                    leadingIcon = { Icon(Icons.Default.CloudDone, null) },
                                    onClick = { 
                                        showMenu = false
                                        serversViewModel.servers.value.forEach { serversViewModel.connectServer(it) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(Localizer.getString("disconnect_all", lang)) },
                                    leadingIcon = { Icon(Icons.Default.CloudOff, null) },
                                    onClick = { 
                                        showMenu = false
                                        chatsViewModel.disconnectAll()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Servers.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Servers.route) { 
                    ServersScreen(
                        viewModel = serversViewModel,
                        chatsViewModel = chatsViewModel,
                        onAddServerClick = { navController.navigate(Screen.AddServer.route) },
                        onEditServerClick = { serverId -> 
                            navController.navigate(Screen.EditServer.createRoute(serverId))
                        }
                    ) { serverId, target ->
                        if (target == "DISCOVERY") {
                            navController.navigate(Screen.ChannelDiscovery.createRoute(serverId))
                        } else {
                            navController.navigate(Screen.ChatDetail.createRoute(serverId, target))
                        }
                    }
                }
                composable(Screen.AddServer.route) {
                    ServerEditScreen(
                        serverId = null,
                        viewModel = serversViewModel,
                        onSave = { server ->
                            serversViewModel.addServer(server)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.EditServer.route,
                    arguments = listOf(navArgument("serverId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
                    ServerEditScreen(
                        serverId = serverId,
                        viewModel = serversViewModel,
                        onSave = { server ->
                            serversViewModel.updateServer(server)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Channels.route) { 
                    val targets by chatsViewModel.activeChannels.collectAsState()
                    ChatsScreen(
                        targets = targets,
                        viewModel = chatsViewModel,
                        onTargetClick = { serverId, target ->
                            navController.navigate(Screen.ChatDetail.createRoute(serverId, target))
                        }
                    )
                }
                composable(Screen.DirectMessages.route) { 
                    val targets by chatsViewModel.activePrivateChats.collectAsState()
                    ChatsScreen(
                        targets = targets,
                        viewModel = chatsViewModel,
                        onTargetClick = { serverId, target ->
                            navController.navigate(Screen.ChatDetail.createRoute(serverId, target))
                        }
                    )
                }
                composable(Screen.Settings.route) { 
                    SettingsScreen(
                        viewModel = chatsViewModel,
                        onNavigateToUserLists = { navController.navigate(Screen.UserLists.route) }
                    )
                }
                composable(Screen.UserLists.route) {
                    UserListsScreen(
                        viewModel = chatsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable(
                    route = Screen.ChannelDiscovery.route,
                    arguments = listOf(navArgument("serverId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
                    ChannelListScreen(
                        serverId = serverId,
                        viewModel = serversViewModel,
                        onBack = { navController.popBackStack() },
                        onJoin = { channel ->
                            serversViewModel.joinChannel(serverId, channel)
                            navController.navigate(Screen.ChatDetail.createRoute(serverId, channel))
                        }
                    )
                }

                composable(
                    route = Screen.ChatDetail.route,
                    arguments = listOf(
                        navArgument("serverId") { type = NavType.LongType },
                        navArgument("target") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
                    val target = backStackEntry.arguments?.getString("target") ?: ""
                    ChatDetailScreen(
                        serverId = serverId,
                        target = target,
                        viewModel = chatsViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToChat = { sId, t ->
                            navController.navigate(Screen.ChatDetail.createRoute(sId, t))
                        },
                        onNavigateToDiscovery = { sId ->
                            navController.navigate(Screen.ChannelDiscovery.createRoute(sId))
                        }
                    )
                }
            }
        }
    }
}
