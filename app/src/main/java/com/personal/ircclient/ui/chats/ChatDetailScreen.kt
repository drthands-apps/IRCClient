package com.personal.ircclient.ui.chats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.ircclient.data.local.entities.HandshakeStatus
import com.personal.ircclient.data.local.entities.MessageEntity
import com.personal.ircclient.data.local.entities.MessageType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    serverId: Long,
    target: String,
    viewModel: ChatsViewModel,
    onBack: () -> Unit,
    onNavigateToChat: (Long, String) -> Unit,
    onNavigateToDiscovery: (Long) -> Unit
) {
    val messages by viewModel.getMessages(serverId, target).collectAsState(initial = emptyList())
    val user by viewModel.getUser(serverId, target).collectAsState(initial = null)
    val channelUsers by viewModel.getChannelUsers(serverId, target).collectAsState(initial = emptyList())
    
    var text by remember { mutableStateOf("") }
    val isStatus = target == "Status"
    val isChannel = target.startsWith("#")
    val isUser = !isStatus && !isChannel
    val context = androidx.compose.ui.platform.LocalContext.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val handleSend = { input: String ->
        if (input.startsWith("/JOIN ", ignoreCase = true)) {
            val channel = input.substring(6).trim()
            viewModel.sendMessage(serverId, target, input)
            onNavigateToChat(serverId, channel)
        } else if (input.startsWith("/PART", ignoreCase = true) || input.startsWith("/QUIT", ignoreCase = true)) {
            viewModel.sendMessage(serverId, target, input)
            onBack()
        } else {
            viewModel.sendMessage(serverId, target, input)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            // Resizing for IRC limits
            val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            scaled.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            viewModel.sendMedia(serverId, target, MessageType.IMAGE, base64)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Users in $target", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                LazyColumn {
                    items(channelUsers) { nick ->
                        ListItem(
                            headlineContent = { Text(nick) },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.clickable {
                                // Action for clicking a user in the list
                                scope.launch { drawerState.close() }
                                onNavigateToChat(serverId, nick)
                            }
                        )
                    }
                }
            }
        },
        gesturesEnabled = isChannel
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = isChannel) {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Text(target)
                            if (user?.encryptionKey != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Lock, contentDescription = "Secure", tint = Color.Green, modifier = Modifier.size(18.dp))
                            }
                            if (isChannel) {
                                Icon(Icons.Default.People, contentDescription = "Users", modifier = Modifier.padding(start = 8.dp).size(18.dp))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isUser && user?.encryptionKey == null) {
                            IconButton(onClick = { viewModel.initiateSecureChat(serverId, target) }) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Start Secure Chat")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                var showFabMenu by remember { mutableStateOf(false) }
                
                Box {
                    FloatingActionButton(onClick = { showFabMenu = true }) {
                        Icon(
                            imageVector = if (isChannel) Icons.Default.Groups 
                                          else if (isStatus) Icons.Default.Terminal 
                                          else Icons.Default.Person, 
                            contentDescription = "Menu"
                        )
                    }
                    
                    DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                        if (isChannel) {
                            DropdownMenuItem(
                                text = { Text("List Users") },
                                onClick = { 
                                    showFabMenu = false
                                    scope.launch { drawerState.open() }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Read Topic") },
                                onClick = { showFabMenu = false; handleSend("/TOPIC $target") }
                            )
                            DropdownMenuItem(
                                text = { Text("Channel Info") },
                                onClick = { showFabMenu = false; handleSend("/MODE $target") }
                            )
                            DropdownMenuItem(
                                text = { Text("Leave Channel") },
                                onClick = { showFabMenu = false; handleSend("/PART $target") }
                            )
                        } else if (isUser) {
                            DropdownMenuItem(
                                text = { Text("Whois") },
                                onClick = { showFabMenu = false; handleSend("/WHOIS $target") }
                            )
                            DropdownMenuItem(
                                text = { Text("Secure Chat Handshake") },
                                onClick = { showFabMenu = false; viewModel.initiateSecureChat(serverId, target) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Server Version") },
                                onClick = { showFabMenu = false; handleSend("/VERSION") }
                            )
                            DropdownMenuItem(
                                text = { Text("List Channels") },
                                onClick = { showFabMenu = false; onNavigateToDiscovery(serverId) }
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.ime)
                ) {
                    if (isUser && user?.secureHandshakeStatus == HandshakeStatus.RECEIVED) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Secure chat requested", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { viewModel.acceptSecureChat(serverId, target) }) {
                                    Text("Accept & Generate Key")
                                }
                            }
                        }
                    }

                    if (isStatus) {
                        StatusQuickActions(
                            currentText = text,
                            onTextChange = { text = it },
                            onAction = { cmd -> 
                                if (cmd == "/LIST") {
                                    onNavigateToDiscovery(serverId)
                                } else {
                                    handleSend(cmd)
                                }
                            }
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (isStatus) "Enter command (e.g. /HELP)..." else "Type a message...") },
                            leadingIcon = {
                                if (!isStatus) {
                                    IconButton(onClick = { imageLauncher.launch("image/*") }) {
                                        Icon(Icons.Default.Add, contentDescription = "Attach")
                                    }
                                }
                            }
                        )
                        if (!isStatus) {
                            IconButton(onClick = { /* Voice simulation */
                                viewModel.sendMedia(serverId, target, MessageType.VOICE, "voice_sample")
                            }) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice")
                            }
                        }
                        IconButton(onClick = {
                            if (text.isNotBlank()) {
                                handleSend(text)
                                text = ""
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                reverseLayout = true,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }
        }
    }
}

@Composable
fun StatusQuickActions(
    currentText: String,
    onTextChange: (String) -> Unit,
    onAction: (String) -> Unit
) {
    val commands = listOf(
        "/LIST" to "List Channels",
        "/MOTD" to "MOTD",
        "/LUSERS" to "Users",
        "/STATS" to "Stats",
        "/WHOIS " to "Whois...",
        "/HELP" to "Help"
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(commands) { (cmd, label) ->
            SuggestionChip(
                onClick = {
                    if (cmd.endsWith(" ")) {
                        onTextChange(cmd)
                    } else {
                        onAction(cmd)
                    }
                },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun MessageBubble(msg: MessageEntity) {
    val isStatus = msg.target == "Status"
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (msg.sender == "me") Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = when {
                msg.sender == "me" -> MaterialTheme.colorScheme.primaryContainer
                isStatus -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (msg.sender != "me") {
                    Text(text = msg.sender, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    text = msg.text,
                    fontFamily = if (isStatus) FontFamily.Monospace else FontFamily.Default,
                    fontSize = if (isStatus) 10.sp else 14.sp,
                    lineHeight = if (isStatus) 12.sp else 20.sp
                )
                
                if (msg.type == MessageType.IMAGE) {
                    val bitmap = remember(msg.text) {
                        try {
                            val bytes = Base64.decode(msg.text, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) { null }
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Image",
                            modifier = Modifier.size(150.dp).padding(top = 8.dp)
                        )
                    }
                } else if (msg.type == MessageType.VOICE) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        Text("Voice Message", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (msg.isModifiedByScript) {
                    Text(
                        text = "Modified by script",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Light,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
