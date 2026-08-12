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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.ircclient.core.utils.Localizer
import com.personal.ircclient.data.local.dao.TargetInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    targets: List<TargetInfo>,
    viewModel: ChatsViewModel,
    onTargetClick: (Long, String) -> Unit
) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val scope = rememberCoroutineScope()

    val isRooms = targets.any { it.target.startsWith("#") }
    
    // Grouping by Server and Sorting
    val groupedTargets = remember(targets) {
        targets.groupBy { it.serverId }.toList().sortedByDescending { pair ->
            pair.second.maxOfOrNull { it.lastVisited ?: 0L } ?: 0L
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (targets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val key = if (isRooms) "no_rooms" else "no_privates"
                Text(Localizer.getString(key, lang))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedTargets.forEach { (serverId, serverTargets) ->
                    item {
                        val serverName by viewModel.getServerName(serverId).collectAsState(initial = "Server $serverId")
                        Column {
                            Text(
                                text = serverName.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        }
                    }

                    val sortedServerTargets = serverTargets.sortedByDescending { it.lastVisited ?: 0L }

                    items(sortedServerTargets, key = { "${it.serverId}_${it.target}" }) { targetInfo ->
                        var showConfirmClose by remember { mutableStateOf(false) }
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    showConfirmClose = true
                                    false
                                } else false
                            }
                        )

                        if (showConfirmClose) {
                            AlertDialog(
                                onDismissRequest = { 
                                    showConfirmClose = false
                                    scope.launch { dismissState.reset() }
                                },
                                title = { Text(Localizer.getString("clear_history", lang)) },
                                text = { Text("${Localizer.getString("clear_history_confirm", lang)} (${targetInfo.target})") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.closeChat(targetInfo.serverId, targetInfo.target)
                                            showConfirmClose = false
                                            scope.launch { dismissState.reset() }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text(Localizer.getString("delete", lang)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { 
                                        showConfirmClose = false
                                        scope.launch { dismissState.reset() }
                                    }) { Text(Localizer.getString("cancel", lang)) }
                                }
                            )
                        }
                        
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
                                        .background(color, shape = MaterialTheme.shapes.medium)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = Localizer.getString("delete", lang),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            ChatListItem(
                                targetInfo = targetInfo,
                                onFavoriteClick = { viewModel.toggleFavorite(targetInfo.serverId, targetInfo.target) },
                                onClick = { onTargetClick(targetInfo.serverId, targetInfo.target) }
                            )
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    targetInfo: TargetInfo,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    val isChannel = targetInfo.target.startsWith("#")
    val isPro = com.personal.ircclient.BuildConfig.FLAVOR == "pro"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isChannel) Icons.Default.ChatBubble else Icons.Default.Person,
                contentDescription = null,
                tint = if (targetInfo.isJoined == true || !isChannel) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = targetInfo.target,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if ((targetInfo.unreadCount ?: 0) > 0) FontWeight.Bold else FontWeight.Normal
                    )
                    if (targetInfo.encryptionKey != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Lock, "Encrypted", tint = Color.Green, modifier = Modifier.size(14.dp))
                    }
                    if (!isChannel && isPro) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    }
                }
                if (!targetInfo.topic.isNullOrBlank()) {
                    Text(
                        text = targetInfo.topic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (targetInfo.isFavorite == true) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = "Favorite",
                    tint = if (targetInfo.isFavorite == true) Color(0xFFFF4500) else Color.Gray, // Contrast Orange-Red
                    modifier = Modifier.size(20.dp)
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
