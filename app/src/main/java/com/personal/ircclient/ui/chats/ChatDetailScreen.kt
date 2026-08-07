package com.personal.ircclient.ui.chats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
    val activeTargets by viewModel.activeTargets.collectAsState()
    val currentTargetInfo = activeTargets.find { it.target == target && it.serverId == serverId }
    val myNick by viewModel.getCurrentNickname(serverId).collectAsState()
    val amIOp by viewModel.isUserOp(serverId, target, myNick).collectAsState(initial = false)

    LaunchedEffect(serverId, target) {
        viewModel.clearUnreadCount(serverId, target)
    }

    var text by remember { mutableStateOf("") }
    val isStatus = target == "Status"
    val isChannel = target.startsWith("#")
    val isUser = !isStatus && !isChannel
    val context = androidx.compose.ui.platform.LocalContext.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

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
                        var showUserMenu by remember { mutableStateOf(false) }
                        val dismissState = rememberSwipeToDismissBoxState()
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            LaunchedEffect(Unit) {
                                if (amIOp) {
                                    handleSend("/MODE $target +b $nick")
                                } else {
                                    viewModel.ignoreUser(serverId, nick, true)
                                }
                                dismissState.reset()
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(if (amIOp) "Ban" else "Ignore", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            Box {
                                ListItem(
                                    headlineContent = { Text(nick) },
                                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        showUserMenu = true
                                    }
                                )
                                
                                DropdownMenu(
                                    expanded = showUserMenu,
                                    onDismissRequest = { showUserMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Private Chat") },
                                        onClick = { 
                                            showUserMenu = false
                                            scope.launch { drawerState.close() }
                                            onNavigateToChat(serverId, nick)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Whois") },
                                        onClick = { showUserMenu = false; handleSend("/WHOIS $nick") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy Nick") },
                                        onClick = { 
                                            showUserMenu = false
                                            clipboardManager.setText(AnnotatedString(nick))
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Ignore User") },
                                        onClick = { showUserMenu = false; viewModel.ignoreUser(serverId, nick, true) }
                                    )
                                    if (amIOp) {
                                        DropdownMenuItem(
                                            text = { Text("Kick $nick", color = MaterialTheme.colorScheme.error) },
                                            onClick = { showUserMenu = false; handleSend("/KICK $target $nick") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Ban $nick", color = MaterialTheme.colorScheme.error) },
                                            onClick = { showUserMenu = false; handleSend("/MODE $target +b $nick") }
                                        )
                                    }
                                }
                            }
                        }
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(target)
                                if (user?.encryptionKey != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Lock, contentDescription = "Secure", tint = Color.Green, modifier = Modifier.size(18.dp))
                                }
                            }
                            currentTargetInfo?.topic?.let { topic ->
                                Text(
                                    text = topic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isChannel) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.People, contentDescription = "Users")
                            }
                        }
                        if (isUser && user?.encryptionKey == null) {
                            IconButton(onClick = { viewModel.initiateSecureChat(serverId, target) }) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Start Secure Chat")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!isStatus) {
                    Column(horizontalAlignment = Alignment.End) {
                        var showFabMenu by remember { mutableStateOf(false) }
                        Box {
                            FloatingActionButton(onClick = { showFabMenu = true }) {
                                Icon(
                                    imageVector = if (isChannel) Icons.Default.Groups 
                                                  else Icons.Default.Person, 
                                    contentDescription = "Menu"
                                )
                            }
                            
                            DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Save Log")
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Checkbox(
                                                checked = currentTargetInfo?.saveLog ?: false,
                                                onCheckedChange = { 
                                                    viewModel.setSaveLog(serverId, target, it)
                                                }
                                            )
                                        }
                                    },
                                    onClick = { viewModel.setSaveLog(serverId, target, !(currentTargetInfo?.saveLog ?: false)) }
                                )
                                HorizontalDivider()
                                
                                val availableCommands = viewModel.getAvailableCommands(serverId, target, amIOp)
                                availableCommands.forEach { cmd ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = when(cmd) {
                                                    "JOIN" -> "Join Channel"
                                                    "PART" -> "Leave Channel"
                                                    "QUIT" -> "Disconnect"
                                                    "NICK" -> "Change Nickname"
                                                    "TOPIC" -> "Set Topic"
                                                    "LIST" -> "List Channels"
                                                    "WHOIS" -> "Whois"
                                                    "KICK" -> "Kick User"
                                                    "BAN" -> "Ban Mask"
                                                    "KICKBAN" -> "Kick & Ban"
                                                    "OP" -> "Give Op"
                                                    "DEOP" -> "Take Op"
                                                    "VOICE" -> "Give Voice"
                                                    "DEVOICE" -> "Take Voice"
                                                    "MODE" -> "Channel Modes"
                                                    "CLEAR" -> "Clear Chat"
                                                    "ME" -> "Action (/me)"
                                                    "NAMES" -> "List Users"
                                                    "INVITE" -> "Invite User"
                                                    "AWAY" -> "Set Away"
                                                    "IGNORE" -> "Ignore User"
                                                    else -> cmd
                                                },
                                                color = if (cmd == "PART" || cmd == "QUIT") MaterialTheme.colorScheme.error else Color.Unspecified
                                            )
                                        },
                                        onClick = { 
                                            showFabMenu = false
                                            if (cmd == "LIST") onNavigateToDiscovery(serverId)
                                            else if (cmd == "PART" || cmd == "QUIT") handleSend("/$cmd")
                                            else text = "/$cmd " // Pre-fill text field
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    FloatingActionButton(onClick = { onNavigateToDiscovery(serverId) }) {
                        Icon(Icons.Default.Search, contentDescription = "List Channels")
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
                    MessageBubble(
                        msg = msg,
                        serverId = serverId,
                        myNick = myNick,
                        amIOp = amIOp,
                        onAction = handleSend,
                        viewModel = viewModel,
                        onNavigateToChat = onNavigateToChat
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(100.dp))
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
fun MessageBubble(
    msg: MessageEntity,
    serverId: Long,
    myNick: String,
    amIOp: Boolean,
    onAction: (String) -> Unit,
    viewModel: ChatsViewModel,
    onNavigateToChat: (Long, String) -> Unit
) {
    val isStatus = msg.target == "Status"
    val isMe = msg.sender == "me" || msg.sender == myNick
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = when {
                isMe -> MaterialTheme.colorScheme.primaryContainer
                isStatus -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.clickable { showMenu = true }
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!isMe && !isStatus && !msg.isSystemMessage) {
                    Text(text = msg.sender, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                } else if (isMe) {
                    Text(text = myNick, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    text = msg.text,
                    fontFamily = if (isStatus) FontFamily.Monospace else FontFamily.Default,
                    fontSize = if (isStatus) 10.sp else 14.sp,
                    lineHeight = if (isStatus) 12.sp else 20.sp
                )
                
                // ... (image/voice handling)
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
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Copy Text") },
                onClick = { 
                    clipboardManager.setText(AnnotatedString(msg.text))
                    showMenu = false
                }
            )
            if (!isMe && !isStatus && !msg.isSystemMessage) {
                DropdownMenuItem(
                    text = { Text("Copy Nick") },
                    onClick = { 
                        clipboardManager.setText(AnnotatedString(msg.sender))
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Private Chat") },
                    onClick = { showMenu = false; onNavigateToChat(serverId, msg.sender) }
                )
                DropdownMenuItem(
                    text = { Text("Whois") },
                    onClick = { showMenu = false; onAction("/WHOIS ${msg.sender}") }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Ignore User") },
                    onClick = { showMenu = false; viewModel.ignoreUser(serverId, msg.sender, true) }
                )
                DropdownMenuItem(
                    text = { Text("Silence User") },
                    onClick = { showMenu = false; onAction("/SILENCE +${msg.sender}") }
                )
                if (amIOp) {
                    DropdownMenuItem(
                        text = { Text("Kick ${msg.sender}", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onAction("/KICK ${msg.target} ${msg.sender}") }
                    )
                    DropdownMenuItem(
                        text = { Text("Ban ${msg.sender}", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onAction("/MODE ${msg.target} +b ${msg.sender}") }
                    )
                }
            } else if (isMe) {
                DropdownMenuItem(
                    text = { Text("Change Nickname") },
                    onClick = { showMenu = false; onAction("/NICK ") }
                )
            }
        }
    }
}
