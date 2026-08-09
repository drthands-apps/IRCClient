package com.personal.ircclient.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.ircclient.core.utils.Localizer
import com.personal.ircclient.data.local.dao.TargetInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    targets: List<TargetInfo>,
    viewModel: ChatsViewModel,
    onTargetClick: (Long, String) -> Unit
) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    Column(modifier = Modifier.fillMaxSize()) {
        if (targets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Check if we are in Rooms or Privates by looking at targets type if possible, 
                // but better use current route or just a generic message if not sure.
                // However, based on how it's called in MainScreen, we can pass a hint.
                // For now, let's try to infer from first target if exists, or just use generic.
                val isRooms = targets.any { it.target.startsWith("#") }
                val key = if (isRooms) "no_rooms" else "no_privates"
                Text(Localizer.getString(key, lang))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(targets, key = { "${it.serverId}_${it.target}" }) { targetInfo ->
                    val serverName by viewModel.getServerName(targetInfo.serverId).collectAsState(initial = "Server ${targetInfo.serverId}")
                    
                    ChatListItem(
                        targetInfo = targetInfo,
                        serverName = serverName,
                        onClick = { onTargetClick(targetInfo.serverId, targetInfo.target) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    targetInfo: TargetInfo,
    serverName: String,
    onClick: () -> Unit
) {
    val isChannel = targetInfo.target.startsWith("#")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isChannel) Icons.Default.ChatBubble else Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = targetInfo.target,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if ((targetInfo.unreadCount ?: 0) > 0) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if ((targetInfo.unreadCount ?: 0) > 0) {
                Badge {
                    Text(text = targetInfo.unreadCount.toString())
                }
            }
        }
    }
}
