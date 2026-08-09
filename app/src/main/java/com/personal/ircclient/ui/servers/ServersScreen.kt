package com.personal.ircclient.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import com.personal.ircclient.core.IrcEngine
import com.personal.ircclient.data.local.entities.ChannelEntity
import com.personal.ircclient.data.local.entities.ServerEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    viewModel: ServersViewModel,
    onAddServerClick: () -> Unit,
    onEditServerClick: (Long) -> Unit,
    onChannelClick: (Long, String) -> Unit
) {
    val servers by viewModel.servers.collectAsState()
    val statuses by viewModel.connectionStatuses.collectAsState()
    
    var serverToDelete by remember { mutableStateOf<ServerEntity?>(null) }

    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Delete Server") },
            text = { Text("Are you sure you want to delete '${serverToDelete?.name}'? This will also disconnect you and clear all history.") },
            confirmButton = {
                Button(
                    onClick = {
                        serverToDelete?.let { viewModel.deleteServer(it) }
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(servers, key = { it.id }) { server ->
            val connectionStatus = statuses[server.id] ?: IrcEngine.ConnectionStatus.DISCONNECTED
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.EndToStart) {
                        viewModel.disconnectServer(server.id)
                        true
                    } else false
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color = when (dismissState.dismissDirection) {
                        SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).background(color), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Default.CloudOff, contentDescription = "Disconnect", tint = Color.White)
                    }
                },
                enableDismissFromStartToEnd = false
            ) {
                ServerItem(
                    server = server,
                    status = connectionStatus,
                    channels = viewModel.getChannels(server.id).collectAsState(initial = emptyList()).value,
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
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onJoinChannel: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onDiscoverClick: () -> Unit
) {
    val isActive = status != IrcEngine.ConnectionStatus.DISCONNECTED
    val isRegistered = status == IrcEngine.ConnectionStatus.REGISTERED
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
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(text = server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            text = "${server.host}:${server.port}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                        Text(
                            text = status.name,
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = if (isActive) onDisconnect else onConnect, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = "Connection Status",
                                modifier = Modifier.size(18.dp),
                                tint = when(status) {
                                    IrcEngine.ConnectionStatus.REGISTERED -> Color.Green
                                    IrcEngine.ConnectionStatus.CONNECTED, IrcEngine.ConnectionStatus.REGISTERING -> Color.Cyan
                                    IrcEngine.ConnectionStatus.CONNECTING -> Color.Yellow
                                    IrcEngine.ConnectionStatus.ERROR -> Color.Red
                                    else -> Color.Gray
                                }
                            )
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        if (isActive) {
                            IconButton(onClick = { onChannelClick("Status") }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = "Console",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                DropdownMenu(expanded = showServerMenu, onDismissRequest = { showServerMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Server Info") },
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
                        Text("Channels", style = MaterialTheme.typography.titleMedium)
                        if (isActive) {
                            TextButton(onClick = { 
                                onJoinChannel("/LIST")
                                onDiscoverClick()
                            }) {
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
                                    SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.5f)
                                    else -> Color.Transparent
                                }
                                Box(modifier = Modifier.fillMaxSize().background(color), contentAlignment = Alignment.CenterEnd) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Part", tint = Color.White)
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            Box {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().combinedClickable(
                                        onClick = { onChannelClick(channel.name) },
                                        onLongClick = { showChannelMenu = true }
                                    ),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(channel.name)
                                        if (channel.isJoined) {
                                            Text("Joined", color = Color.Green)
                                        } else if (isRegistered) {
                                            TextButton(onClick = { 
                                                onJoinChannel(channel.name)
                                                onChannelClick(channel.name)
                                            }) {
                                                Text("Join")
                                            }
                                        }
                                    }
                                }
                                
                                DropdownMenu(expanded = showChannelMenu, onDismissRequest = { showChannelMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Topic Info") },
                                        onClick = { showChannelMenu = false; onJoinChannel("/TOPIC ${channel.name}") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Leave Channel") },
                                        onClick = { showChannelMenu = false; onJoinChannel("/PART ${channel.name}") }
                                    )
                                }
                            }
                        }
                    }
                    if (isRegistered) {
                        var newChannel by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newChannel,
                                onValueChange = { newChannel = it },
                                label = { Text("Join channel...") },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (newChannel.isNotBlank()) {
                                    val formatted = if (newChannel.startsWith("#")) newChannel else "#$newChannel"
                                    onJoinChannel(formatted)
                                    onChannelClick(formatted)
                                    newChannel = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Join")
                            }
                        }
                    }
                }
            }
        }
    }
}
