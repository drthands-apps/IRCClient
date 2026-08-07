package com.personal.ircclient.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.personal.ircclient.ui.chats.ChatDetailScreen
import com.personal.ircclient.ui.chats.ChatsScreen
import com.personal.ircclient.ui.chats.ChatsViewModel
import com.personal.ircclient.ui.discovery.ChannelListScreen
import com.personal.ircclient.ui.navigation.Screen
import com.personal.ircclient.ui.servers.AddServerScreen
import com.personal.ircclient.ui.servers.ServersScreen
import com.personal.ircclient.ui.servers.ServersViewModel
import com.personal.ircclient.ui.settings.SettingsScreen

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

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        Screen.Servers,
        Screen.Chats,
        Screen.Settings
    )

    val totalUnread by chatsViewModel.totalUnreadCount.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { 
                            BadgedBox(
                                badge = {
                                    if (screen == Screen.Chats && totalUnread > 0) {
                                        Badge { Text(totalUnread.toString()) }
                                    }
                                }
                            ) {
                                Icon(screen.icon, contentDescription = screen.title)
                            }
                        },
                        label = { Text(screen.title) },
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
                    FloatingActionButton(onClick = { navController.navigate(Screen.AddServer.route) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Server")
                    }
                }
                Screen.Chats.route -> {
                    FloatingActionButton(onClick = { /* TODO: New Private Chat */ }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "New Chat")
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
                    onAddServerClick = { navController.navigate(Screen.AddServer.route) }
                ) { serverId, target ->
                    if (target == "DISCOVERY") {
                        navController.navigate(Screen.ChannelDiscovery.createRoute(serverId))
                    } else {
                        navController.navigate(Screen.ChatDetail.createRoute(serverId, target))
                    }
                }
            }
            composable(Screen.AddServer.route) {
                AddServerScreen(
                    onSave = { server ->
                        serversViewModel.addServer(server)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Chats.route) { 
                ChatsScreen(
                    viewModel = chatsViewModel,
                    onTargetClick = { serverId, target ->
                        navController.navigate(Screen.ChatDetail.createRoute(serverId, target))
                    }
                )
            }
            composable(Screen.Settings.route) { 
                SettingsScreen(viewModel = chatsViewModel)
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
