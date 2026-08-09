package com.personal.ircclient.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.ircclient.data.local.dao.TargetInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    targets: List<TargetInfo>,
    viewModel: ChatsViewModel,
    onTargetClick: (Long, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Active Conversations",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        if (targets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active conversations here.")
            }
        } else {
            LazyColumn {
                items(targets, key = { "${it.serverId}_${it.target}" }) { targetInfo ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                         LaunchedEffect(Unit) {
                             viewModel.closeChat(targetInfo.serverId, targetInfo.target)
                             dismissState.reset()
                         }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Close", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        TargetItem(
                            targetInfo = targetInfo,
                            viewModel = viewModel,
                            onClick = { 
                                if (targetInfo.target.startsWith("#") && targetInfo.isJoined == false) {
                                    viewModel.sendMessage(targetInfo.serverId, targetInfo.target, "/JOIN ${targetInfo.target}")
                                }
                                onTargetClick(targetInfo.serverId, targetInfo.target)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TargetItem(targetInfo: TargetInfo, viewModel: ChatsViewModel, onClick: () -> Unit) {
    val isStatus = targetInfo.target == "Status"
    val isChannel = targetInfo.target.startsWith("#")
    val serverName by viewModel.getServerName(targetInfo.serverId).collectAsState(initial = "Server ${targetInfo.serverId}")
    
    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(targetInfo.target)
                if (targetInfo.isBanned == true) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("BANNED", color = Color.White) }
                } else if (isChannel && targetInfo.isJoined == true) {
                    Badge(
                        containerColor = Color(0xFF4CAF50),
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("JOINED", color = Color.White) }
                } else if (isChannel && targetInfo.isJoined == false) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("DISCONNECTED", color = Color.White) }
                }
            }
        },
        supportingContent = { 
            val type = if (isStatus) "Server Console" else if (isChannel) "Channel" else "Private Chat"
            Text("$type • $serverName")
        },
        leadingContent = {
            BadgedBox(
                badge = {
                    if ((targetInfo.unreadCount ?: 0) > 0) {
                        Badge { Text(targetInfo.unreadCount.toString()) }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isStatus) Icons.Default.Terminal 
                                 else if (isChannel) Icons.Default.ChatBubble 
                                 else Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
