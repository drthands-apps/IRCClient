package com.personal.ircclient.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.ircclient.core.utils.Localizer
import com.personal.ircclient.core.IrcEngine
import com.personal.ircclient.data.local.entities.ChannelEntity
import com.personal.ircclient.data.local.entities.ServerEntity
import com.personal.ircclient.ui.chats.ChatsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    viewModel: ServersViewModel,
    chatsViewModel: ChatsViewModel,
    onAddServerClick: () -> Unit,
    onEditServerClick: (Long) -> Unit,
    onChannelClick: (Long, String) -> Unit
) {
    val servers by viewModel.servers.collectAsState()
    val statuses by viewModel.connectionStatuses.collectAsState()
    val settings by chatsViewModel.settingsState.collectAsState()
    val lang = settings.language
    val scope = rememberCoroutineScope()
    
    var serverToDelete by remember { mutableStateOf<ServerEntity?>(null) }

    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text(Localizer.getString("delete_server", lang)) },
            text = { Text(Localizer.getString("delete_server_confirm", lang)) },
            confirmButton = {
                Button(
                    onClick = {
                        serverToDelete?.let { viewModel.deleteServer(it) }
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(Localizer.getString("delete", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) { Text(Localizer.getString("cancel", lang)) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (servers.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Localizer.getString("no_servers", lang))
                }
            }
        }
        items(servers, key = { it.id }) { server ->
            val connectionStatus = statuses[server.id] ?: IrcEngine.ConnectionStatus.DISCONNECTED
            
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.EndToStart) {
                        if (connectionStatus != IrcEngine.ConnectionStatus.DISCONNECTED) {
                            viewModel.disconnectServer(server.id)
                        }
                        true
                    } else false
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color = when (dismissState.dismissDirection) {
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .background(color, shape = MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = Localizer.getString("disconnect", lang),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                },
                enableDismissFromStartToEnd = false
            ) {
                ServerItem(
                    server = server,
                    status = connectionStatus,
                    channels = viewModel.getChannels(server.id).collectAsState(initial = emptyList()).value,
                    lang = lang,
                    onConnect = { viewModel.connectServer(server) },
                    onDisconnect = { viewModel.disconnectServer(server.id) },
                    onEdit = { onEditServerClick(server.id) },
                    onDelete = { serverToDelete = server },
                    onJoinChannel = { viewModel.joinChannel(server.id, it) },
                    onChannelClick = { onChannelClick(server.id, it) },
                    onDiscoverClick = { onChannelClick(server.id, "DISCOVERY") }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerItem(
    server: ServerEntity,
    status: IrcEngine.ConnectionStatus,
    channels: List<ChannelEntity>,
    lang: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onJoinChannel: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onDiscoverClick: () -> Unit
) {
    val isActive = status != IrcEngine.ConnectionStatus.DISCONNECTED
    var expanded by remember { mutableStateOf(false) }
    var showServerMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { showServerMenu = true }
            )
    ) {
        Column {
            Box {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(text = server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(text = "${server.host}:${server.port}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        val statusKey = when(status) {
                            IrcEngine.ConnectionStatus.REGISTERED -> "joined"
                            IrcEngine.ConnectionStatus.DISCONNECTED -> "disconnected"
                            else -> status.name.lowercase()
                        }
                        Text(
                            text = Localizer.getString(statusKey, lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = when(status) {
                                IrcEngine.ConnectionStatus.REGISTERED -> Color.Green
                                IrcEngine.ConnectionStatus.CONNECTED, IrcEngine.ConnectionStatus.REGISTERING -> Color.Cyan
                                IrcEngine.ConnectionStatus.CONNECTING -> Color.Yellow
                                IrcEngine.ConnectionStatus.ERROR -> Color.Red
                                else -> Color.Gray
                            }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = if (isActive) onDisconnect else onConnect, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isActive) Color.Green else Color.Gray
                            )
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                        if (isActive) {
                            IconButton(onClick = { onChannelClick("Status") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                DropdownMenu(expanded = showServerMenu, onDismissRequest = { showServerMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(Localizer.getString("server_info", lang)) },
                        onClick = { showServerMenu = false; onJoinChannel("/VERSION") }
                    )
                    DropdownMenuItem(
                        text = { Text("List Users") },
                        onClick = { showServerMenu = false; onJoinChannel("/LUSERS") }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Localizer.getString("nav_rooms", lang), style = MaterialTheme.typography.titleMedium)
                        if (isActive) {
                            TextButton(onClick = { onJoinChannel("/LIST"); onDiscoverClick() }) {
                                Text("List All")
                            }
                        }
                    }
                    channels.forEach { channel ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    onJoinChannel("/PART ${channel.name}")
                                    true
                                } else false
                            }
                        )
                        var showChannelMenu by remember { mutableStateOf(false) }
                        
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color, shape = MaterialTheme.shapes.small)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { onChannelClick(channel.name) },
                                    onLongClick = { showChannelMenu = true }
                                ),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (channel.isJoined) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (channel.isJoined) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = channel.name, style = MaterialTheme.typography.bodyMedium)
                                        if (channel.unreadCount > 0) {
                                            Badge(modifier = Modifier.padding(start = 8.dp)) { Text(channel.unreadCount.toString()) }
                                        }
                                    }
                                    if (!channel.isJoined) {
                                        TextButton(onClick = { onJoinChannel(channel.name); onChannelClick(channel.name) }) {
                                            Text(Localizer.getString("connect", lang))
                                        }
                                    }
                                }
                            }
                        }
                        DropdownMenu(expanded = showChannelMenu, onDismissRequest = { showChannelMenu = false }) {
                            DropdownMenuItem(text = { Text("Topic Info") }, onClick = { showChannelMenu = false; onJoinChannel("/TOPIC ${channel.name}") })
                            DropdownMenuItem(text = { Text("Leave Channel") }, onClick = { showChannelMenu = false; onJoinChannel("/PART ${channel.name}") })
                        }
                    }
                }
            }
        }
    }
}
