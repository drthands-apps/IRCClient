package com.personal.ircclient.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    var showColorPickerBy by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("Appearance") }

    val categories = listOf(
        "Appearance" to Icons.Default.Palette,
        "Security & Privacy" to Icons.Default.Security,
        "Audio & Events" to Icons.Default.VolumeUp,
        "Connectivity" to Icons.Default.Cloud,
        "ASCII & Phrases" to Icons.Default.ArtTrack,
        "About" to Icons.Default.Info
    )

    if (showColorPickerBy != null) {
        ColorPickerDialog(
            onDismiss = { showColorPickerBy = null },
            onColorSelected = { color ->
                when (showColorPickerBy) {
                    "own" -> viewModel.updateSettings(settings.copy(ownMessageColor = color))
                    "other" -> viewModel.updateSettings(settings.copy(otherBubbleColor = color))
                    "bubble" -> viewModel.updateSettings(settings.copy(ownBubbleColor = color))
                }
                showColorPickerBy = null
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Rail for Categories
        NavigationRail(
            modifier = Modifier.width(80.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            categories.forEach { (name, icon) ->
                NavigationRailItem(
                    selected = selectedCategory == name,
                    onClick = { selectedCategory = name },
                    icon = { Icon(icon, contentDescription = name) },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Main Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (selectedCategory) {
                "Appearance" -> AppearanceSettings(settings, viewModel) { showColorPickerBy = it }
                "Security & Privacy" -> SecuritySettings(settings, viewModel, onNavigateToUserLists)
                "Audio & Events" -> AudioSettings(settings, viewModel)
                "Connectivity" -> ConnectivitySettings(settings, viewModel)
                "ASCII & Phrases" -> AsciiManagementSettings(viewModel)
                "About" -> AboutSettings(context)
            }
        }
    }
}

@Composable
fun AsciiManagementSettings(viewModel: ChatsViewModel) {
    val items by viewModel.asciiArt.collectAsState()
    var showAddDialog by remember { mutableStateOf<com.personal.ircclient.data.local.entities.AsciiArtEntity?>(null) }
    var showColorHelp by remember { mutableStateOf(false) }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAddDialog = com.personal.ircclient.data.local.entities.AsciiArtEntity(name = "", content = "") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null)
                Text("Add New")
            }
            OutlinedButton(onClick = { showColorHelp = true }) {
                Icon(Icons.Default.ColorLens, null)
            }
        }
        
        if (showColorHelp) {
            AlertDialog(
                onDismissRequest = { showColorHelp = false },
                title = { Text("IRC Color Codes") },
                text = {
                    Text("Use ctrl+k equivalent: \\u0003 followed by 00-15.\nExample: \\u000304Red Text\nBold: \\u0002, Reset: \\u000f")
                },
                confirmButton = { TextButton(onClick = { showColorHelp = false }) { Text("Got it") } }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Text("No items saved yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            items.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).clickable { showAddDialog = item }) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (item.isPhrase) "Phrase" else "ASCII Art",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.deleteAsciiArt(item) }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog != null) {
        var name by remember { mutableStateOf(showAddDialog!!.name) }
        var content by remember { mutableStateOf(showAddDialog!!.content) }
        var isPhrase by remember { mutableStateOf(showAddDialog!!.isPhrase) }

        AlertDialog(
            onDismissRequest = { showAddDialog = null },
            title = { Text(if (showAddDialog!!.id == 0L) "Add Item" else "Edit Item") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content, 
                        onValueChange = { content = it }, 
                        label = { Text("Content (Multi-line)") }, 
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPhrase, onCheckedChange = { isPhrase = it })
                        Text("Is this a simple phrase?")
                    }
                    if (content.contains("\\u")) {
                        Text("Note: Color codes will be parsed on send.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && content.isNotBlank()) {
                        viewModel.insertAsciiArt(name, content, isPhrase, showAddDialog?.id ?: 0L)
                        showAddDialog = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AppearanceSettings(
    settings: com.personal.ircclient.data.local.entities.SettingsEntity,
    viewModel: ChatsViewModel,
    onPickColor: (String) -> Unit
) {
    Column {
        val themes = mapOf("LIGHT" to "Light Mode", "DARK" to "Night Mode", "OLED" to "True Black (OLED)")
        var expandedTheme by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(onClick = { expandedTheme = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Theme: ${themes[settings.themeName] ?: settings.themeName}")
            }
            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                themes.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        viewModel.updateSettings(settings.copy(themeName = key))
                        expandedTheme = false
                    })
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SecurityToggleItem(
            title = "Enable IRC Colors",
            subtitle = "Parse and show IRC color codes",
            checked = settings.enableIrcColors,
            onCheckedChange = { viewModel.updateSettings(settings.copy(enableIrcColors = it)) }
        )
        
        ColorPickerItem("Identity Color", "Nick & border color", settings.ownMessageColor) { onPickColor("own") }
        ColorPickerItem("Incoming Bubbles", "Background for others", settings.otherBubbleColor) { onPickColor("other") }
        ColorPickerItem("Own Bubbles", "Your background", settings.ownBubbleColor) { onPickColor("bubble") }
        
        SecurityToggleItem(
            title = "High Contrast",
            subtitle = "Sharper text visibility",
            checked = settings.useHighContrast,
            onCheckedChange = { viewModel.updateSettings(settings.copy(useHighContrast = it)) }
        )
    }
}

@Composable
fun SecuritySettings(
    settings: com.personal.ircclient.data.local.entities.SettingsEntity,
    viewModel: ChatsViewModel,
    onNavigateToUserLists: () -> Unit
) {
    Column {
        SettingsItem("User Lists", "Friends & Ignored", Icons.Default.People, onNavigateToUserLists)
        
        SecurityToggleItem("Only Friends PV", "Block direct messages from strangers", settings.allowPrivateOnlyFromFriends) {
            viewModel.updateSettings(settings.copy(allowPrivateOnlyFromFriends = it))
        }

        if (settings.allowPrivateOnlyFromFriends) {
            var autoMsg by remember { mutableStateOf(settings.autoResponseForBlockedPv) }
            OutlinedTextField(
                value = autoMsg,
                onValueChange = { 
                    autoMsg = it
                    viewModel.updateSettings(settings.copy(autoResponseForBlockedPv = it)) 
                },
                label = { Text("Auto-response Message") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        SecurityToggleItem("Link Previews", "Show content previews", settings.showLinkPreviews) {
            viewModel.updateSettings(settings.copy(showLinkPreviews = it))
        }
        SecurityToggleItem("Auto-load Images", "Privacy risk", settings.autoLoadImages) {
            viewModel.updateSettings(settings.copy(autoLoadImages = it))
        }

        Spacer(Modifier.height(16.dp))
        Text("Away Messages", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        
        var awayMsg by remember { mutableStateOf(settings.defaultAwayMessage) }
        OutlinedTextField(
            value = awayMsg,
            onValueChange = { 
                awayMsg = it
                viewModel.updateSettings(settings.copy(defaultAwayMessage = it)) 
            },
            label = { Text("Default Away Message (/AWAY)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        
        var backMsg by remember { mutableStateOf(settings.defaultBackMessage) }
        OutlinedTextField(
            value = backMsg,
            onValueChange = { 
                backMsg = it
                viewModel.updateSettings(settings.copy(defaultBackMessage = it)) 
            },
            label = { Text("Default Back Message (/BACK)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        var userAgent by remember { mutableStateOf(settings.customUserAgent) }
        OutlinedTextField(
            value = userAgent,
            onValueChange = { 
                userAgent = it
                viewModel.updateSettings(settings.copy(customUserAgent = it)) 
            },
            label = { Text("UserAgent / RealName") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
    }
}

@Composable
fun AudioSettings(
    settings: com.personal.ircclient.data.local.entities.SettingsEntity,
    viewModel: ChatsViewModel
) {
    Column {
        SecurityToggleItem("Text-to-Speech", "Read messages aloud", viewModel.isTtsActive) {
            viewModel.setTtsEnabled(it)
        }

        SettingsSoundItem("Friend Join", settings.soundOnFriendJoin) { viewModel.setSoundOnFriendJoin(it) }
        SettingsSoundItem("Private Message", settings.soundOnPrivateMessage) { viewModel.setSoundOnPrivateMessage(it) }
        SettingsSoundItem("Notices", settings.soundOnNotice) { viewModel.updateSettings(settings.copy(soundOnNotice = it)) }
        SettingsSoundItem("Mentions", settings.soundOnMention) { viewModel.updateSettings(settings.copy(soundOnMention = it)) }

        Spacer(Modifier.height(16.dp))
        Text("Events Display", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        
        SecurityToggleItem("Show Events in Room", "Global visibility for system events", viewModel.showEventsInRoom) {
            viewModel.setShowEventsInRoomEnabled(it)
        }
        
        if (viewModel.showEventsInRoom) {
            EventSettingItem("Joins", viewModel.joinDisplayMode == EventDisplayMode.ROOM) { viewModel.setJoinDisplayEnabled(it) }
            EventSettingItem("Parts", viewModel.partDisplayMode == EventDisplayMode.ROOM) { viewModel.setPartDisplayEnabled(it) }
            EventSettingItem("Quits", viewModel.quitDisplayMode == EventDisplayMode.ROOM) { viewModel.setQuitDisplayEnabled(it) }
            EventSettingItem("Nicks", viewModel.nickChangeDisplayMode == EventDisplayMode.ROOM) { viewModel.setNickChangeDisplayEnabled(it) }
            EventSettingItem("Kicks", viewModel.kickDisplayMode == EventDisplayMode.ROOM) { viewModel.setKickDisplayEnabled(it) }
            EventSettingItem("Bans", viewModel.banDisplayMode == EventDisplayMode.ROOM) { viewModel.setBanDisplayEnabled(it) }
        }
    }
}

@Composable
fun ConnectivitySettings(
    settings: com.personal.ircclient.data.local.entities.SettingsEntity,
    viewModel: ChatsViewModel
) {
    Column {
        val languages = mapOf(
            "en" to "English",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch",
            "pt" to "Português",
            "zh" to "中文"
        )
        var expandedLang by remember { mutableStateOf(false) }
        
        Box {
            OutlinedButton(onClick = { expandedLang = true }, modifier = Modifier.fillMaxWidth()) {
                Text("App Language: ${languages[settings.language] ?: settings.language}")
            }
            DropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }) {
                languages.forEach { (code, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        viewModel.updateSettings(settings.copy(language = code))
                        expandedLang = false
                    })
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SecurityToggleItem("Stay Persistent", "Maintain connection in background", settings.runInBackground) {
            viewModel.updateSettings(settings.copy(runInBackground = it))
        }

        var searchEngine by remember { mutableStateOf(settings.defaultSearchEngine) }
        OutlinedTextField(
            value = searchEngine,
            onValueChange = { 
                searchEngine = it
                viewModel.updateSettings(settings.copy(defaultSearchEngine = it)) 
            },
            label = { Text("Search Engine URL") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        Spacer(Modifier.height(16.dp))
        SecurityToggleItem("Use Proxy", "SOCKS/HTTP", settings.useProxy) {
            viewModel.updateSettings(settings.copy(useProxy = it))
        }
        
        if (settings.useProxy) {
            var pHost by remember { mutableStateOf(settings.proxyHost) }
            OutlinedTextField(
                value = pHost, 
                onValueChange = { 
                    pHost = it
                    viewModel.updateSettings(settings.copy(proxyHost = it)) 
                }, 
                label = { Text("Host") }
            )
            var pPort by remember { mutableStateOf(settings.proxyPort.toString()) }
            OutlinedTextField(
                value = pPort, 
                onValueChange = { 
                    pPort = it
                    viewModel.updateSettings(settings.copy(proxyPort = it.toIntOrNull() ?: 1080)) 
                }, 
                label = { Text("Port") }
            )
        }
    }
}

@Composable
fun AboutSettings(context: android.content.Context) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.LogoDev, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("FenixIRC", style = MaterialTheme.typography.headlineMedium)
        Text("Version 0.1 (Alpha)", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { /* Check updates */ }) {
            Text("Check for Updates")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { (context as? Activity)?.finish() }) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
            Spacer(Modifier.width(8.dp))
            Text("Exit FenixIRC")
        }
    }
}

@Composable
fun ColorPickerDialog(onDismiss: () -> Unit, onColorSelected: (Long) -> Unit) {
    val colors = listOf(
        0xFFBB86FC, 0xFF6200EE, 0xFF3700B3, 0xFF03DAC6, 
        0xFF018786, 0xFFCF6679, 0xFFB00020, 0xFFFFB74D,
        0xFF81C784, 0xFF4FC3F7, 0xFFBA68C8, 0xFF90A4AE,
        0xFFFFFFFF, 0xFF000000, 0xFF333333, 0xFFEEEEEE
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column {
                colors.chunked(4).forEach { chunk ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        chunk.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(color), shape = MaterialTheme.shapes.small)
                                    .clickable { onColorSelected(color) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ColorPickerItem(title: String, subtitle: String, color: Long, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
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
fun SecurityToggleItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSoundItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f))
    }
}

@Composable
fun EventSettingItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.7f))
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
