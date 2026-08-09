package com.personal.ircclient.ui.discovery

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.ircclient.data.local.entities.ChannelDiscoveryEntity
import com.personal.ircclient.ui.servers.ServersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    serverId: Long,
    viewModel: ServersViewModel,
    onBack: () -> Unit,
    onJoin: (String) -> Unit
) {
    val channels by viewModel.getDiscoveredChannels(serverId).collectAsState(initial = emptyList())
    
    LaunchedEffect(serverId) {
        if (channels.isEmpty()) {
            viewModel.refreshChannelList(serverId)
        }
    }

    var sortMode by remember { mutableStateOf("users") } // "name", "users"
    var minUsers by remember { mutableStateOf(0) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val filteredChannels = remember(channels, sortMode, minUsers) {
        channels.filter { it.userCount >= minUsers }
            .sortedWith(if (sortMode == "users") compareByDescending { it.userCount } else compareBy { it.channelName.lowercase() })
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter Channels") },
            text = {
                Column {
                    Text("Minimum Users: $minUsers")
                    Slider(
                        value = minUsers.toFloat(),
                        onValueChange = { minUsers = it.toInt() },
                        valueRange = 0f..500f,
                        steps = 50
                    )
                }
            },
            confirmButton = { Button(onClick = { showFilterDialog = false }) { Text("Apply") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channels (${filteredChannels.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshChannelList(serverId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { sortMode = if (sortMode == "users") "name" else "users" }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredChannels) { channel ->
                ChannelBubble(channel = channel, onClick = { onJoin(channel.channelName) })
            }
        }
    }
}

@Composable
fun ChannelBubble(channel: ChannelDiscoveryEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Badge {
                    Text("${channel.userCount} users")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (channel.topic.isBlank()) "No topic set" else channel.topic,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}
