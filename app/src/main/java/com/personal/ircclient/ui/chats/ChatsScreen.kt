package com.personal.ircclient.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.ircclient.data.local.entities.UserEntity

@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    onTargetClick: (Long, String) -> Unit
) {
    val targets by viewModel.activeTargets.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Active Chats",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        if (targets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active conversations yet.")
            }
        } else {
            LazyColumn {
                items(targets) { targetInfo ->
                    TargetItem(
                        target = targetInfo.target,
                        serverId = targetInfo.serverId,
                        viewModel = viewModel,
                        onClick = { onTargetClick(targetInfo.serverId, targetInfo.target) }
                    )
                }
            }
        }
    }
}

@Composable
fun TargetItem(target: String, serverId: Long, viewModel: ChatsViewModel, onClick: () -> Unit) {
    val isStatus = target == "Status"
    val isChannel = target.startsWith("#")
    val serverName by viewModel.getServerName(serverId).collectAsState(initial = "Server $serverId")
    
    ListItem(
        headlineContent = { Text(target) },
        supportingContent = { 
            val type = if (isStatus) "Server Console" else if (isChannel) "Channel" else "Private Chat"
            Text("$type • $serverName")
        },
        leadingContent = {
            Icon(
                imageVector = if (isStatus) Icons.Default.Terminal 
                             else if (isChannel) Icons.Default.ChatBubble 
                             else Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
