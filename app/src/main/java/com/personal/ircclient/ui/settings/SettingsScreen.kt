package com.personal.ircclient.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.ircclient.data.local.entities.EventDisplayMode
import com.personal.ircclient.ui.chats.ChatsViewModel

@Composable
fun SettingsScreen(
    viewModel: ChatsViewModel,
    onNavigateToUserLists: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()
    var showColorPickerBy by remember { mutableStateOf<String?>(null) } // "own", "other", "bubble"

    if (showColorPickerBy != null) {
        val colors = listOf(
            0xFFBB86FC, 0xFF6200EE, 0xFF3700B3, 0xFF03DAC6, 
            0xFF018786, 0xFFCF6679, 0xFFB00020, 0xFFFFB74D,
            0xFF81C784, 0xFF4FC3F7, 0xFFBA68C8, 0xFF90A4AE,
            0xFFFFFFFF, 0xFF000000, 0xFF333333, 0xFFEEEEEE
        )
        AlertDialog(
            onDismissRequest = { showColorPickerBy = null },
            title = { Text("Select Color") },
            text = {
                Column {
                    val chunks = colors.chunked(4)
                    chunks.forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            chunk.forEach { color ->
                                ColorOption(color) { 
                                    when (showColorPickerBy) {
                                        "own" -> viewModel.updateSettings(settings.copy(ownMessageColor = color))
                                        "other" -> viewModel.updateSettings(settings.copy(otherBubbleColor = color))
                                        "bubble" -> viewModel.updateSettings(settings.copy(ownBubbleColor = color))
                                    }
                                    showColorPickerBy = null
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPickerBy = null }) { Text("Close") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        SettingsSection(title = "Appearance") {
            val themes = mapOf("LIGHT" to "Light Mode", "DARK" to "Night Mode", "OLED" to "True Black (OLED)")
            var expandedTheme by remember { mutableStateOf(false) }

            Box(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(onClick = { expandedTheme = true }) {
                    Text("Theme: ${themes[settings.themeName] ?: settings.themeName}")
                }
                DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                    themes.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateSettings(settings.copy(themeName = key))
                                expandedTheme = false
                            }
                        )
                    }
                }
            }

            SecurityToggleItem(
                title = "Enable IRC Colors",
                subtitle = "Parse and show IRC color codes",
                checked = settings.enableIrcColors,
                onCheckedChange = { viewModel.updateSettings(settings.copy(enableIrcColors = it)) }
            )
            
            ColorPickerItem(
                title = "Accent Color (Nick & Borders)",
                subtitle = "Color for your identity",
                color = settings.ownMessageColor,
                onClick = { showColorPickerBy = "own" }
            )
            
            ColorPickerItem(
                title = "Other Users Bubble",
                subtitle = "Background for incoming messages",
                color = settings.otherBubbleColor,
                onClick = { showColorPickerBy = "other" }
            )

            ColorPickerItem(
                title = "Your Bubble Background",
                subtitle = "Background for your own messages",
                color = settings.ownBubbleColor,
                onClick = { showColorPickerBy = "bubble" }
            )
        }

        SettingsSection(title = "Search & Browser") {
            OutlinedTextField(
                value = settings.defaultSearchEngine,
                onValueChange = { viewModel.updateSettings(settings.copy(defaultSearchEngine = it)) },
                label = { Text("Default Search Engine URL") },
                placeholder = { Text("https://www.google.com/search?q=") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }

        SettingsSection(title = "Network & Session") {
            val servers by viewModel.allServers.collectAsState()
            servers.forEach { server ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reconnect channels (${server.name})", style = MaterialTheme.typography.bodyLarge)
                        Text("Auto-join open channels on connect", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = server.reconnectOpenChannels,
                        onCheckedChange = { viewModel.setServerReconnectChannels(server.id, it) }
                    )
                }
            }
        }

        SettingsSection(title = "Security & Privacy") {
            SettingsItem(
                title = "User Lists",
                subtitle = "Friends, ignored and silenced users",
                icon = Icons.Default.People,
                onClick = onNavigateToUserLists
            )
            
            SecurityToggleItem(
                title = "Link Previews",
                subtitle = "Show previews for YouTube, images, etc.",
                checked = settings.showLinkPreviews,
                onCheckedChange = { viewModel.updateSettings(settings.copy(showLinkPreviews = it)) }
            )
            
            SecurityToggleItem(
                title = "Auto-load Images",
                subtitle = "Load image links automatically (Privacy risk)",
                checked = settings.autoLoadImages,
                onCheckedChange = { viewModel.updateSettings(settings.copy(autoLoadImages = it)) }
            )

            SecurityToggleItem(
                title = "Use Proxy",
                subtitle = "Enable SOCKS/HTTP proxy for connections",
                checked = settings.useProxy,
                onCheckedChange = { viewModel.updateSettings(settings.copy(useProxy = it)) }
            )
            
            if (settings.useProxy) {
                OutlinedTextField(
                    value = settings.proxyHost,
                    onValueChange = { viewModel.updateSettings(settings.copy(proxyHost = it)) },
                    label = { Text("Proxy Host") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                OutlinedTextField(
                    value = settings.proxyPort.toString(),
                    onValueChange = { viewModel.updateSettings(settings.copy(proxyPort = it.toIntOrNull() ?: 1080)) },
                    label = { Text("Proxy Port") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            OutlinedTextField(
                value = settings.customUserAgent,
                onValueChange = { viewModel.updateSettings(settings.copy(customUserAgent = it)) },
                label = { Text("Custom User Agent (RealName)") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            SecurityToggleItem(
                title = "Open Links Externally",
                subtitle = "Open links in system browser",
                checked = settings.openLinksExternally,
                onCheckedChange = { viewModel.updateSettings(settings.copy(openLinksExternally = it)) }
            )
            
            SecurityToggleItem(
                title = "Friend Notifications",
                subtitle = "Notify when friends connect to the server",
                checked = settings.enableFriendNotify,
                onCheckedChange = { viewModel.updateSettings(settings.copy(enableFriendNotify = it)) }
            )
            
            if (settings.openLinksExternally) {
                var expandedBrowser by remember { mutableStateOf(false) }
                val browsers = mapOf(
                    "SYSTEM_DEFAULT" to "System Default",
                    "com.android.chrome" to "Google Chrome",
                    "org.mozilla.firefox" to "Mozilla Firefox",
                    "com.duckduckgo.mobile.android" to "DuckDuckGo",
                    "com.brave.browser" to "Brave Browser"
                )
                
                Box(modifier = Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { expandedBrowser = true }) {
                        Text("Browser: ${browsers[settings.preferredBrowser] ?: settings.preferredBrowser}")
                    }
                    DropdownMenu(expanded = expandedBrowser, onDismissRequest = { expandedBrowser = false }) {
                        browsers.forEach { (pkg, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.updateSettings(settings.copy(preferredBrowser = pkg))
                                    expandedBrowser = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsItem(
                title = "Encryption",
                subtitle = "Manage keys and secure chats",
                icon = Icons.Default.Security,
                onClick = { /* TODO */ }
            )

            SecurityToggleItem(
                title = "Only Friends PV",
                subtitle = "Block private messages from strangers",
                checked = settings.allowPrivateOnlyFromFriends,
                onCheckedChange = { viewModel.updateSettings(settings.copy(allowPrivateOnlyFromFriends = it)) }
            )
            
            if (settings.allowPrivateOnlyFromFriends) {
                OutlinedTextField(
                    value = settings.autoResponseForBlockedPv,
                    onValueChange = { viewModel.updateSettings(settings.copy(autoResponseForBlockedPv = it)) },
                    label = { Text("Auto-response for blocked messages") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }

        SettingsSection(title = "Accessibility & Audio") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Read Messages", style = MaterialTheme.typography.bodyLarge)
                        Text("Automatic text-to-speech", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Switch(
                    checked = viewModel.isTtsActive,
                    onCheckedChange = { viewModel.setTtsEnabled(it) }
                )
            }

            SettingsSoundItem(
                title = "Friend Entrance Sound",
                checked = settings.soundOnFriendJoin,
                onCheckedChange = { viewModel.setSoundOnFriendJoin(it) }
            )
            SettingsSoundItem(
                title = "Private Message Sound",
                checked = settings.soundOnPrivateMessage,
                onCheckedChange = { viewModel.setSoundOnPrivateMessage(it) }
            )
            SettingsSoundItem(
                title = "Ban Notification Sound",
                checked = settings.soundOnBan,
                onCheckedChange = { viewModel.setSoundOnBan(it) }
            )
            SettingsSoundItem(
                title = "Notice Received Sound",
                checked = settings.soundOnNotice,
                onCheckedChange = { viewModel.updateSettings(settings.copy(soundOnNotice = it)) }
            )
            SettingsSoundItem(
                title = "Mention Sound",
                checked = settings.soundOnMention,
                onCheckedChange = { viewModel.updateSettings(settings.copy(soundOnMention = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Show Events in Room", style = MaterialTheme.typography.bodyLarge)
                        Text("Joins, parts, etc. in chat window", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Switch(
                    checked = viewModel.showEventsInRoom,
                    onCheckedChange = { viewModel.setShowEventsInRoomEnabled(it) }
                )
            }
            
            if (viewModel.showEventsInRoom) {
                Column(modifier = Modifier.padding(start = 32.dp)) {
                    EventSettingItem(title = "Joins", checked = viewModel.joinDisplayMode == EventDisplayMode.ROOM, onCheckedChange = { viewModel.setJoinDisplayEnabled(it) })
                    EventSettingItem(title = "Parts", checked = viewModel.partDisplayMode == EventDisplayMode.ROOM, onCheckedChange = { viewModel.setPartDisplayEnabled(it) })
                    EventSettingItem(title = "Quits", checked = viewModel.quitDisplayMode == EventDisplayMode.ROOM, onCheckedChange = { viewModel.setQuitDisplayEnabled(it) })
                    EventSettingItem(title = "Nick Changes", checked = viewModel.nickChangeDisplayMode == EventDisplayMode.ROOM, onCheckedChange = { viewModel.setNickChangeDisplayEnabled(it) })
                    EventSettingItem(title = "Kicks", checked = viewModel.kickDisplayMode == EventDisplayMode.ROOM, onCheckedChange = { viewModel.setKickDisplayEnabled(it) })
                    EventSettingItem(title = "Bans", checked = viewModel.banDisplayMode == EventDisplayMode.ROOM, onCheckedChange = { viewModel.setBanDisplayEnabled(it) })
                }
            }
        }

        SettingsSection(title = "Application") {
            val settings by viewModel.settingsState.collectAsState()
            
            SecurityToggleItem(
                title = "Run in Background",
                subtitle = "Keep connections active when app is closed",
                checked = settings.runInBackground,
                onCheckedChange = { viewModel.updateSettings(settings.copy(runInBackground = it)) }
            )

            SettingsItem(
                title = "Exit Application",
                subtitle = "Close and disconnect all servers",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = {
                    viewModel.disconnectAll()
                    (context as? Activity)?.finish()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "IRC Client v1.0",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun ColorPickerItem(
    title: String,
    subtitle: String,
    color: Long,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(color), shape = MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
        )
    }
}

@Composable
fun ColorOption(color: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color(color), shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    )
}

@Composable
fun SecurityToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSoundItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun EventSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.7f)
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
