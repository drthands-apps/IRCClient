package com.personal.ircclient.ui.chats

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.ircclient.data.local.entities.HandshakeStatus
import com.personal.ircclient.data.local.entities.MessageEntity
import com.personal.ircclient.data.local.entities.MessageType
import com.personal.ircclient.data.local.entities.UserStatus
import com.personal.ircclient.core.utils.Localizer
import com.personal.ircclient.core.utils.LinkHandler
import com.personal.ircclient.core.utils.ImageUtils
import com.personal.ircclient.core.utils.FileUploader
import com.personal.ircclient.core.audio.AudioRecorder
import com.personal.ircclient.core.audio.RadioPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val channelUsers by remember(serverId, target) { viewModel.getChannelUsersWithInfo(serverId, target) }.collectAsState(initial = emptyList())
    val activeTargets by viewModel.activeTargets.collectAsState()
    val currentTargetInfo = activeTargets.find { it.target == target && it.serverId == serverId }
    val myNick by viewModel.getCurrentNickname(serverId).collectAsState()
    val amIOp by viewModel.isUserOp(serverId, target, myNick).collectAsState(initial = false)
    val usersForServer by viewModel.allChatUsers.collectAsState()
    val friends = usersForServer.filter { it.serverId == serverId && it.isFriend }.map { it.nickname }.toSet()
    val banList by viewModel.getBanList(serverId, target).collectAsState(initial = emptySet())

    LaunchedEffect(serverId, target) {
        viewModel.clearUnreadCount(serverId, target)
        if (target.startsWith("#")) {
            viewModel.refreshBanList(serverId, target)
        }
    }

    DisposableEffect(serverId, target) {
        onDispose {
            viewModel.onLeaveChat(serverId)
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val isStatus = target == "Status"
    val isChannel = target.startsWith("#")
    val isUser = !isStatus && !isChannel
    val contextAndroid = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val recorder = remember { AudioRecorder(contextAndroid) }
    var isRecording by remember { mutableStateOf(false) }
    
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecording = true
            recorder.startRecording()
        }
    }

    var showAttachMenu by remember { mutableStateOf(false) }
    var showYoutubeSearch by remember { mutableStateOf(false) }
    var showQuickSearch by remember { mutableStateOf(false) }
    var showFormattingTools by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAsciiSelector by remember { mutableStateOf<String?>(null) } // target user nick
    var searchQuery by remember { mutableStateOf("") }
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val asciiArtItems by viewModel.asciiArt.collectAsState()

    if (showAsciiSelector != null) {
        AlertDialog(
            onDismissRequest = { showAsciiSelector = null },
            title = { Text(Localizer.getString("ascii_phrases", lang)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (asciiArtItems.isEmpty()) {
                        Text(Localizer.getString("no_ascii", lang))
                    } else {
                        asciiArtItems.forEach { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = { Text(if (item.isPhrase) "Phrase" else "ASCII Art") },
                                modifier = Modifier.clickable {
                                    viewModel.sendAsciiArt(serverId, showAsciiSelector!!, item)
                                    showAsciiSelector = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAsciiSelector = null }) { Text(Localizer.getString("close", lang)) }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(Localizer.getString("clear_history", lang)) },
            text = { Text(Localizer.getString("clear_history_confirm", lang)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendMessage(serverId, target, "/CLEAR")
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(Localizer.getString("cancel", lang)) }
            }
        )
    }

    var showTtlDialog by remember { mutableStateOf(false) }
    var mediaToSend by remember { mutableStateOf<Pair<MessageType, String>?>(null) }
    val ttlOptions = listOf(
        Localizer.getString("no_limit", lang) to null,
        "30 Seconds" to 30L,
        "1 Minute" to 60L,
        "5 Minutes" to 300L,
        "1 Hour" to 3600L
    )

    if (showTtlDialog && mediaToSend != null) {
        AlertDialog(
            onDismissRequest = { showTtlDialog = false },
            title = { Text(Localizer.getString("self_destruct", lang)) },
            text = {
                Column {
                    Text("Select how long the media will be visible:")
                    Spacer(Modifier.height(8.dp))
                    ttlOptions.forEach { (label, value) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.sendMedia(serverId, target, mediaToSend!!.first, mediaToSend!!.second, value)
                                showTtlDialog = false
                                mediaToSend = null
                            }.padding(12.dp)
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTtlDialog = false; mediaToSend = null }) { Text(Localizer.getString("cancel", lang)) }
            }
        )
    }

    if (showYoutubeSearch) {
        AlertDialog(
            onDismissRequest = { showYoutubeSearch = false },
            title = { Text(Localizer.getString("music_search", lang)) },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Song name, artist...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val encoded = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                        val url = "https://www.youtube.com/results?search_query=$encoded"
                        clipboardManager.setText(AnnotatedString(url))
                        showYoutubeSearch = false
                        searchQuery = ""
                    }) { Text(Localizer.getString("copy_link", lang)) }
                    Button(onClick = {
                        if (searchQuery.isNotBlank()) {
                            val encoded = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                            LinkHandler.openLink(contextAndroid, "https://www.youtube.com/results?search_query=$encoded", settings)
                            showYoutubeSearch = false
                            searchQuery = ""
                        }
                    }) { Text(Localizer.getString("search", lang)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showYoutubeSearch = false }) { Text(Localizer.getString("cancel", lang)) }
            }
        )
    }

    if (showQuickSearch) {
        AlertDialog(
            onDismissRequest = { showQuickSearch = false },
            title = { Text(Localizer.getString("quick_search", lang)) },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Ask anything...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (searchQuery.isNotBlank()) {
                        val encoded = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                        LinkHandler.openLink(contextAndroid, "${settings.defaultSearchEngine}$encoded", settings)
                        showQuickSearch = false
                        searchQuery = ""
                    }
                }) { Text(Localizer.getString("search", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showQuickSearch = false }) { Text(Localizer.getString("cancel", lang)) }
            }
        )
    }

    val handleSend = { input: String ->
        if (input.startsWith("/ART_SELECTOR ", ignoreCase = true)) {
            val nick = input.removePrefix("/ART_SELECTOR ").trim()
            showAsciiSelector = nick
        } else if (input.startsWith("/JOIN ", ignoreCase = true)) {
            val channel = input.substring(6).trim().substringBefore(" ")
            viewModel.sendMessage(serverId, target, input)
            onNavigateToChat(serverId, channel)
        } else if (input.startsWith("/QUERY ", ignoreCase = true) || input.startsWith("/MSG ", ignoreCase = true)) {
            val parts = input.split(" ")
            if (parts.size >= 2) {
                val newTarget = parts[1]
                viewModel.sendMessage(serverId, target, input)
                onNavigateToChat(serverId, newTarget)
            } else {
                viewModel.sendMessage(serverId, target, input)
            }
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
            val inputStream = contextAndroid.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val watermarked = ImageUtils.addWatermark(bitmap, "IRCClient Secure")
            
            if (isUser) {
                val tempFile = java.io.File(contextAndroid.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                val fos = java.io.FileOutputStream(tempFile)
                watermarked.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, fos)
                fos.close()
                
                FileUploader.uploadFile(tempFile) { url ->
                    if (url != null) {
                        mediaToSend = MessageType.IMAGE to url
                        showTtlDialog = true
                    }
                }
            }
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val inputStream = contextAndroid.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes() ?: return@let
            
            if (isUser) {
                val tempFile = java.io.File(contextAndroid.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                val fos = java.io.FileOutputStream(tempFile)
                fos.write(bytes)
                fos.close()
                
                FileUploader.uploadFile(tempFile) { url ->
                    if (url != null) {
                        mediaToSend = MessageType.VOICE to url
                        showTtlDialog = true
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Users in $target", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                val sortedUsers = remember(channelUsers) {
                    channelUsers.sortedWith(
                        compareByDescending<ChannelUserInfo> { it.prefix.isNotEmpty() }
                            .thenByDescending { it.prefix == "@" || it.prefix == "&" || it.prefix == "~" }
                            .thenByDescending { it.isFriend }
                            .thenBy { it.nickname.lowercase() }
                    )
                }

                val listState = rememberLazyListState()
                val alphabet = ('A'..'Z').map { it.toString() }
                
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(sortedUsers, key = { it.nickname }) { userInfo ->
                            val nick = userInfo.nickname
                            var showUserMenu by rememberSaveable { mutableStateOf(false) }
                            var showConfirmIgnore by remember { mutableStateOf(false) }
                            
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        showConfirmIgnore = true
                                        false
                                    } else false
                                }
                            )

                            if (showConfirmIgnore) {
                                AlertDialog(
                                    onDismissRequest = { 
                                        showConfirmIgnore = false
                                        scope.launch { dismissState.reset() }
                                    },
                                    title = { Text(if (amIOp) Localizer.getString("ban_user", lang) else Localizer.getString("ignore", lang)) },
                                    text = { Text("Are you sure you want to ${if (amIOp) "ban" else "ignore"} $nick?") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                if (amIOp) {
                                                    handleSend("/MODE $target +b $nick")
                                                } else {
                                                    viewModel.ignoreUser(serverId, nick, UserStatus.DEFINITIVE)
                                                }
                                                showConfirmIgnore = false
                                                scope.launch { dismissState.reset() }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) { Text(if (amIOp) Localizer.getString("ban", lang) else Localizer.getString("ignore", lang)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { 
                                            showConfirmIgnore = false
                                            scope.launch { dismissState.reset() }
                                        }) { Text(Localizer.getString("cancel", lang)) }
                                    }
                                )
                            }

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(if (amIOp) Localizer.getString("ban", lang) else Localizer.getString("ignore", lang), color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                },
                                enableDismissFromStartToEnd = false
                            ) {
                                Box {
                                    Surface(
                                        onClick = { showUserMenu = true },
                                        color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ListItem(
                                            headlineContent = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = nick,
                                                        color = if (userInfo.ignoreStatus != UserStatus.NONE) 
                                                                    MaterialTheme.colorScheme.outline 
                                                                else Color.Unspecified
                                                    )
                                                    if (userInfo.isFriend) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            leadingContent = { 
                                                Box(
                                                    modifier = Modifier.size(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = when {
                                                            userInfo.prefix == "@" || userInfo.prefix == "&" || userInfo.prefix == "~" -> Icons.Default.Shield
                                                            userInfo.prefix == "+" || userInfo.prefix == "%" -> Icons.Default.VolumeUp
                                                            userInfo.isFriend -> Icons.Default.Person
                                                            else -> Icons.Default.PersonOutline
                                                        },
                                                        contentDescription = null,
                                                        tint = if (userInfo.ignoreStatus != UserStatus.NONE) 
                                                                    MaterialTheme.colorScheme.outline 
                                                                else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showUserMenu,
                                        onDismissRequest = { showUserMenu = false }
                                    ) {
                                        Text(
                                            text = nick,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(Localizer.getString("private_chat", settings.language)) },
                                            leadingIcon = { Icon(Icons.Default.Message, null) },
                                            onClick = { 
                                                showUserMenu = false
                                                scope.launch { drawerState.close() }
                                                onNavigateToChat(serverId, nick)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(Localizer.getString("whois", settings.language)) },
                                            onClick = { showUserMenu = false; handleSend("/WHOIS $nick") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(Localizer.getString("copy_nick", lang)) },
                                            onClick = { 
                                                showUserMenu = false
                                                clipboardManager.setText(AnnotatedString(nick))
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(if (userInfo.isFriend) "Unfriend User" else "Friend User") },
                                            leadingIcon = { Icon(Icons.Default.Star, null) },
                                            onClick = { showUserMenu = false; viewModel.setFriend(serverId, nick, !userInfo.isFriend) }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Send Art/Phrase") },
                                            leadingIcon = { Icon(Icons.Default.ArtTrack, null) },
                                            onClick = { 
                                                showUserMenu = false
                                                showAsciiSelector = nick
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(Localizer.getString("ignore", settings.language) + " (Definitive)") },
                                            onClick = { showUserMenu = false; viewModel.ignoreUser(serverId, nick, UserStatus.DEFINITIVE) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(Localizer.getString("ignore", settings.language) + " (Temporal)") },
                                            onClick = { showUserMenu = false; viewModel.ignoreUser(serverId, nick, UserStatus.TEMPORAL) }
                                        )
                                        if (amIOp) {
                                            val isOp = userInfo.prefix == "@" || userInfo.prefix == "&" || userInfo.prefix == "~"
                                            DropdownMenuItem(
                                                text = { Text(if (isOp) Localizer.getString("deop_user", lang) else Localizer.getString("op_user", lang)) },
                                                onClick = { 
                                                    showUserMenu = false
                                                    viewModel.setOp(serverId, target, nick, !isOp)
                                                }
                                            )
                                            
                                            val banMask = userInfo.hostmask?.let { "*!*@${it.substringAfter("@")}" } ?: "$nick!*@*"
                                            val isActuallyBanned = banList.contains(banMask) || banList.any { it.contains(nick) }
                                            
                                            DropdownMenuItem(
                                                text = { Text(if (isActuallyBanned) "Unban $nick" else Localizer.getString("ban_user", lang), color = MaterialTheme.colorScheme.error) },
                                                onClick = { 
                                                    showUserMenu = false
                                                    viewModel.banUser(serverId, target, banMask, !isActuallyBanned)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Localizer.getString("kick_user", lang) + " $nick", color = MaterialTheme.colorScheme.error) },
                                                onClick = { showUserMenu = false; handleSend("/KICK $target $nick") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        alphabet.forEach { letter ->
                            Text(
                                text = letter,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val index = sortedUsers.indexOfFirst { it.nickname.startsWith(letter, ignoreCase = true) }
                                        if (index != -1) {
                                            listState.animateScrollToItem(index)
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
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
                                if (!isStatus) {
                                    Text(
                                        text = if (settings.enableIrcColors) parseIrcColors(topic) else AnnotatedString(stripIrcColors(topic)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (!isStatus) {
                            if (isChannel) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.People, contentDescription = null)
                                }
                            }
                            if (isUser && user?.encryptionKey == null) {
                                IconButton(onClick = { viewModel.initiateSecureChat(serverId, target) }) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null)
                                }
                            }
                            
                            var showMoreActions by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMoreActions = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }
                                DropdownMenu(expanded = showMoreActions, onDismissRequest = { showMoreActions = false }) {
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
                                    DropdownMenuItem(
                                        text = { Text(Localizer.getString("music_search", lang)) },
                                        leadingIcon = { Icon(Icons.Default.MusicNote, null) },
                                        onClick = { 
                                            showMoreActions = false
                                            showYoutubeSearch = true 
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localizer.getString("quick_search", lang)) },
                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                        onClick = { 
                                            showMoreActions = false
                                            showQuickSearch = true
                                        }
                                    )
                                    HorizontalDivider()
                                    
                                    val availableCommands = viewModel.getAvailableCommands(serverId, target, amIOp)
                                    availableCommands.forEach { cmd ->
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    text = when(cmd) {
                                                        "JOIN" -> Localizer.getString("join_room", lang)
                                                        "PART" -> "Leave Channel"
                                                        "QUIT" -> "Disconnect"
                                                        "NICK" -> "Change Nickname"
                                                        "TOPIC" -> "Set Topic"
                                                        "LIST" -> Localizer.getString("discover", lang)
                                                        "WHOIS" -> "Whois"
                                                        "KICK" -> "Kick User"
                                                        "BAN" -> "Ban Mask"
                                                        "KICKBAN" -> "Kick & Ban"
                                                        "OP" -> "Give Op"
                                                        "DEOP" -> "Take Op"
                                                        "VOICE" -> "Give Voice"
                                                        "DEVOICE" -> "Take Voice"
                                                        "MODE" -> "Channel Modes"
                                                        "CLEAR" -> Localizer.getString("clear_history", lang)
                                                        "ME" -> "Action (/me)"
                                                        "NAMES" -> "List Users"
                                                        "INVITE" -> "Invite User"
                                                        "AWAY" -> "Set Away"
                                                        "IGNORE" -> Localizer.getString("ignore", lang)
                                                        else -> cmd
                                                    },
                                                    color = if (cmd == "PART" || cmd == "QUIT") MaterialTheme.colorScheme.error else Color.Unspecified
                                                )
                                            },
                                            onClick = { 
                                                showMoreActions = false
                                                if (cmd == "LIST") onNavigateToDiscovery(serverId)
                                                else if (cmd == "CLEAR") showClearConfirm = true
                                                else if (cmd == "PART" || cmd == "QUIT") handleSend("/$cmd")
                                                else {
                                                    val commandString = "/$cmd "
                                                    textFieldValue = TextFieldValue(
                                                        text = commandString,
                                                        selection = TextRange(commandString.length)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
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
                    } else if (isUser && user?.secureHandshakeStatus == HandshakeStatus.REQUESTED) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Waiting for user to accept secure chat...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (isStatus) {
                        StatusQuickActions(
                            currentText = textFieldValue.text,
                            onTextChange = { txt ->
                                textFieldValue = TextFieldValue(txt, TextRange(txt.length))
                            },
                            onAction = { cmd -> 
                                if (cmd == "/LIST") {
                                    onNavigateToDiscovery(serverId)
                                } else {
                                    handleSend(cmd)
                                }
                            }
                        )
                    }

                    if (showFormattingTools) {
                        FormattingTools(
                            onFormatClick = { code ->
                                val text = textFieldValue.text
                                val selection = textFieldValue.selection
                                val newText = text.substring(0, selection.start) + code + text.substring(selection.end)
                                textFieldValue = TextFieldValue(newText, TextRange(selection.start + code.length))
                            },
                            onColorClick = { colorCode ->
                                val text = textFieldValue.text
                                val selection = textFieldValue.selection
                                val code = "\u0003$colorCode"
                                val newText = text.substring(0, selection.start) + code + text.substring(selection.end)
                                textFieldValue = TextFieldValue(newText, TextRange(selection.start + code.length))
                            }
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isStatus) {
                            IconButton(onClick = { showFormattingTools = !showFormattingTools }) {
                                Icon(
                                    Icons.Default.FormatColorText, 
                                    contentDescription = null,
                                    tint = if (showFormattingTools) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        var showNickSuggestions by remember { mutableStateOf(false) }
                        val suggestions = remember(textFieldValue.text, channelUsers) {
                            val lastWord = textFieldValue.text.substringBeforeLast(" ", "").let { 
                                textFieldValue.text.substring(it.length).trim() 
                            }
                            if (lastWord.startsWith("@") && lastWord.length > 1) {
                                val query = lastWord.substring(1).lowercase()
                                channelUsers.filter { it.nickname.lowercase().contains(query) }.map { it.nickname }
                            } else emptyList()
                        }

                        LaunchedEffect(suggestions) {
                            showNickSuggestions = suggestions.isNotEmpty()
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(if (isStatus) Localizer.getString("enter_command", lang) else Localizer.getString("typing_message", lang)) },
                                leadingIcon = {
                                    if (!isStatus && !isChannel) {
                                        Box {
                                            IconButton(onClick = { showAttachMenu = true }) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                            }
                                            DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Image") },
                                                    leadingIcon = { Icon(Icons.Default.Image, null) },
                                                    onClick = { showAttachMenu = false; imageLauncher.launch("image/*") }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Audio File") },
                                                    leadingIcon = { Icon(Icons.Default.AudioFile, null) },
                                                    onClick = { showAttachMenu = false; audioLauncher.launch("audio/*") }
                                                )
                                            }
                                        }
                                    }
                                }
                            )

                            if (showNickSuggestions) {
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(y = (-60).dp)
                                        .fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                        items(suggestions) { nick ->
                                            ListItem(
                                                headlineContent = { Text(nick) },
                                                modifier = Modifier.clickable {
                                                    val text = textFieldValue.text
                                                    val lastWordStart = text.lastIndexOf("@")
                                                    val newText = text.substring(0, lastWordStart) + nick
                                                    textFieldValue = TextFieldValue(newText + " ", TextRange(newText.length + 1))
                                                    showNickSuggestions = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!isStatus && !isChannel) {
                            IconButton(onClick = { 
                                if (isRecording) {
                                    val file = recorder.stopRecording()
                                    if (file != null) {
                                        val bytes = file.readBytes()
                                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                        mediaToSend = MessageType.VOICE to base64
                                        showTtlDialog = true
                                    }
                                    isRecording = false
                                } else {
                                    recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.StopCircle else Icons.Default.Mic, 
                                    contentDescription = null,
                                    tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(onClick = {
                            if (textFieldValue.text.isNotBlank()) {
                                handleSend(textFieldValue.text)
                                textFieldValue = TextFieldValue("")
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        }
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                if (settings.isRadioPluginEnabled) {
                    val streamUrl = settings.selectedRadioUrl
                    val radioName = settings.selectedRadioName
                    
                    if (streamUrl.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            val isPlayingByPlayer by RadioPlayer.isPlaying.collectAsState()
                            val currentUrlByPlayer by RadioPlayer.currentUrl.collectAsState()
                            val isCurrentStation = currentUrlByPlayer == streamUrl && isPlayingByPlayer

                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (isCurrentStation) Icons.Default.PauseCircle else Icons.Default.Radio, 
                                        contentDescription = null,
                                        tint = if (isCurrentStation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isCurrentStation) "Playing $radioName..." else "Radio: $radioName", 
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        RadioPlayer.play(contextAndroid, streamUrl)
                                    }) {
                                        Icon(
                                            imageVector = if (isCurrentStation) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                ) {
                    items(messages) { msg ->
                        val senderInfo = channelUsers.find { it.nickname == msg.sender }
                        MessageBubble(
                            msg = msg,
                            serverId = serverId,
                            myNick = myNick,
                            amIOp = amIOp,
                            isFriend = friends.contains(msg.sender),
                            senderPrefix = senderInfo?.prefix ?: "",
                            onAction = handleSend,
                            onTextChange = { textFieldValue = it },
                            viewModel = viewModel,
                            settings = settings,
                            onNavigateToChat = onNavigateToChat,
                            contextAndroid = contextAndroid,
                            senderInfo = senderInfo
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FormattingTools(
    onFormatClick: (String) -> Unit,
    onColorClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { onFormatClick("\u0002") }) { Icon(Icons.Default.FormatBold, "Bold") }
            IconButton(onClick = { onFormatClick("\u001d") }) { Icon(Icons.Default.FormatItalic, "Italic") }
            IconButton(onClick = { onFormatClick("\u001f") }) { Icon(Icons.Default.FormatUnderlined, "Underline") }
            IconButton(onClick = { onFormatClick("\u000f") }) { Icon(Icons.Default.FormatClear, "Reset") }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(IrcColorMap.keys.toList().filter { it.length == 2 }.distinct()) { code ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(IrcColorMap[code]!!, shape = MaterialTheme.shapes.small)
                        .clickable { onColorClick(code) }
                )
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
    isFriend: Boolean,
    senderPrefix: String,
    onAction: (String) -> Unit,
    onTextChange: (TextFieldValue) -> Unit,
    viewModel: ChatsViewModel,
    settings: com.personal.ircclient.data.local.entities.SettingsEntity,
    onNavigateToChat: (Long, String) -> Unit,
    contextAndroid: android.content.Context,
    senderInfo: ChannelUserInfo? = null
) {
    val lang = settings.language
    val isStatus = msg.target == "Status"
    val isMe = msg.sender == "me" || msg.sender == myNick
    val isSystem = msg.isSystemMessage
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val banList by viewModel.getBanList(serverId, msg.target).collectAsState(initial = emptySet())
    
    val dismissState = rememberSwipeToDismissBoxState()
    var showConfirmIgnore by remember { mutableStateOf(false) }

    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
        showConfirmIgnore = true
    }

    if (showConfirmIgnore) {
        AlertDialog(
            onDismissRequest = { 
                showConfirmIgnore = false
                scope.launch { dismissState.reset() }
            },
            title = { Text(Localizer.getString("ignore", lang)) },
            text = { Text("Are you sure you want to ignore ${msg.sender}?") },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isMe && !isSystem) {
                            viewModel.ignoreUser(serverId, msg.sender, UserStatus.DEFINITIVE)
                        }
                        showConfirmIgnore = false
                        scope.launch { dismissState.reset() }
                    }
                ) { Text(Localizer.getString("ignore", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showConfirmIgnore = false
                    scope.launch { dismissState.reset() }
                }) { Text(Localizer.getString("cancel", lang)) }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = when {
            isSystem -> Alignment.CenterHorizontally
            isMe -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Color.Gray else Color.Transparent
                Box(modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    if (!isMe && !isSystem) Text(Localizer.getString("ignore", lang), color = Color.White)
                }
            },
            enableDismissFromStartToEnd = false,
            modifier = Modifier.fillMaxWidth(if (isSystem) 1f else 0.85f)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = when {
                    msg.type == MessageType.NOTICE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    isMe -> Color(settings.ownBubbleColor)
                    isStatus || isSystem -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color(settings.otherBubbleColor)
                },
                border = when {
                    msg.type == MessageType.NOTICE -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                    isMe -> BorderStroke(1.dp, Color(settings.otherBubbleColor))
                    else -> null
                },
                modifier = Modifier.clickable { showMenu = true }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (msg.type == MessageType.NOTICE) {
                            Text(text = "${Localizer.getString("notice", lang)}: ${msg.sender}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                        } else if (!isMe && !isStatus && !isSystem) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = msg.sender, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                if (isFriend) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                }
                            }
                        } else if (isMe) {
                            Text(text = myNick, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(settings.ownMessageColor))
                        }
                        
                        Text(
                            text = timeFormatter.format(Date(msg.timestamp)),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    val messageText = if (settings.enableIrcColors) {
                        parseIrcColors(msg.text, settings)
                    } else {
                        AnnotatedString(stripIrcColors(msg.text))
                    }
                    
                    val currentContext = contextAndroid 
                    
                    val isMOTD = msg.text.contains("MOTD", ignoreCase = true) || isStatus
                    val lines = msg.text.split("\n")
                    val isDrawing = lines.size > 3 || (isMOTD && lines.any { it.contains("  ") })
                    
                    if (isDrawing) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.05f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                lines.forEach { line ->
                                    Text(
                                        text = stripIrcColors(line),
                                        style = TextStyle(
                                            fontSize = 5.sp,
                                            lineHeight = 6.sp,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    } else {
                        ClickableText(
                            text = messageText,
                            style = LocalTextStyle.current.copy(
                                fontFamily = if (isStatus) FontFamily.Monospace else FontFamily.Default,
                                fontSize = if (isStatus) 10.sp else 14.sp,
                                lineHeight = if (isStatus) 12.sp else 20.sp,
                                textAlign = if (isSystem) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = if (isSystem) Modifier.fillMaxWidth() else Modifier,
                            onClick = { offset ->
                                messageText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        LinkHandler.openLink(currentContext, annotation.item, settings)
                                    }
                            }
                        )
                    }
                    
                    if (settings.showLinkPreviews) {
                        // Image Preview
                        val imageRegex = Regex("(https?://[\\w\\d:#@%/;$()~_?\\+-=\\.&]+\\.(?:png|jpg|jpeg|gif|webp))", RegexOption.IGNORE_CASE)
                        val imageMatch = imageRegex.find(msg.text)
                        if (imageMatch != null) {
                            val imageUrl = imageMatch.value
                            var loadAttempted by remember { mutableStateOf(settings.autoLoadImages) }
                            
                            if (loadAttempted) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .clickable {
                                            LinkHandler.openLink(contextAndroid, imageUrl, settings)
                                        }
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { loadAttempted = true },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(Localizer.getString("loading_image_risk", lang))
                                }
                            }
                        }

                        // YouTube Preview
                        val youtubeRegex = Regex("(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]+)")
                        val ytMatch = youtubeRegex.find(msg.text)
                        if (ytMatch != null) {
                            val videoId = ytMatch.groupValues[1]
                            Card(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.Red)
                                        Spacer(Modifier.width(8.dp))
                                        Text(Localizer.getString("youtube_video", lang), style = MaterialTheme.typography.labelMedium)
                                    }
                                    
                                    if (settings.autoLoadImages) {
                                        AsyncImage(
                                            model = "https://img.youtube.com/vi/$videoId/0.jpg",
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clickable {
                                                    LinkHandler.openLink(contextAndroid, "https://www.youtube.com/watch?v=$videoId", settings)
                                                }
                                        )
                                    }

                                    Text(
                                        "https://www.youtube.com/watch?v=$videoId",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp).clickable {
                                            LinkHandler.openLink(contextAndroid, "https://www.youtube.com/watch?v=$videoId", settings)
                                        }
                                    )
                                }
                            }
                        }

                        // Social Media Preview (Generic)
                        val socialRegex = Regex("(https?://(?:www\\.)?(?:twitter\\.com|x\\.com|instagram\\.com|facebook\\.com|tiktok\\.com)/[\\w\\d:#@%/;$()~_?\\+-=\\.&]+)", RegexOption.IGNORE_CASE)
                        val socialMatch = socialRegex.find(msg.text)
                        if (socialMatch != null) {
                            val socialUrl = socialMatch.value
                            Card(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Blue)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = Localizer.getString("social_media_link", lang),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            LinkHandler.openLink(contextAndroid, socialUrl, settings)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    if (msg.type == MessageType.IMAGE) {
                        val isUrl = msg.text.startsWith("http")
                        if (isUrl) {
                            AsyncImage(
                                model = msg.text,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .clickable {
                                        LinkHandler.openLink(contextAndroid, msg.text, settings)
                                    }
                            )
                        } else {
                            val bitmap = remember(msg.text) {
                                try {
                                    val bytes = Base64.decode(msg.text, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (e: Exception) { null }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(150.dp).padding(top = 8.dp)
                                )
                            }
                        }
                    } else if (msg.type == MessageType.VOICE) {
                        val isUrl = msg.text.startsWith("http")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow, 
                                contentDescription = null,
                                modifier = Modifier.clickable {
                                    if (isUrl) {
                                        LinkHandler.openLink(contextAndroid, msg.text, settings)
                                    }
                                }
                            )
                            Text(if (isUrl) Localizer.getString("voice_message_ext", lang) else Localizer.getString("voice_message", lang), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (msg.isModifiedByScript) {
                        Text(
                            text = Localizer.getString("modified_by_script", lang),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Light,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            Text(
                text = msg.sender,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(Localizer.getString("copy_text", lang)) },
                onClick = { 
                    clipboardManager.setText(AnnotatedString(msg.text))
                    showMenu = false
                }
            )
            if (!isMe && !isStatus && !msg.isSystemMessage) {
                DropdownMenuItem(
                    text = { Text(Localizer.getString("copy_nick", lang)) },
                    onClick = { 
                        clipboardManager.setText(AnnotatedString(msg.sender))
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(Localizer.getString("private_chat", settings.language)) },
                    leadingIcon = { Icon(Icons.Default.Message, null) },
                    onClick = { showMenu = false; onNavigateToChat(serverId, msg.sender) }
                )
                DropdownMenuItem(
                    text = { Text(Localizer.getString("notice", lang)) },
                    onClick = { 
                        showMenu = false
                        val commandString = "/NOTICE ${msg.sender} "
                        onTextChange(TextFieldValue(commandString, TextRange(commandString.length)))
                    }
                )
                DropdownMenuItem(
                    text = { Text(Localizer.getString("whois", settings.language)) },
                    onClick = { showMenu = false; onAction("/WHOIS ${msg.sender}") }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (isFriend) "Unfriend User" else "Friend User") },
                    leadingIcon = { Icon(Icons.Default.Star, null) },
                    onClick = { showMenu = false; viewModel.setFriend(serverId, msg.sender, !isFriend) }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Send Art/Phrase") },
                    leadingIcon = { Icon(Icons.Default.ArtTrack, null) },
                    onClick = { 
                        showMenu = false
                        onAction("/ART_SELECTOR ${msg.sender}") // I'll need to handle this special trigger
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(Localizer.getString("ignore", settings.language) + " (Definitive)") },
                    onClick = { showMenu = false; viewModel.ignoreUser(serverId, msg.sender, UserStatus.DEFINITIVE) }
                )
                DropdownMenuItem(
                    text = { Text(Localizer.getString("ignore", settings.language) + " (Temporal)") },
                    onClick = { showMenu = false; viewModel.ignoreUser(serverId, msg.sender, UserStatus.TEMPORAL) }
                )
                if (amIOp) {
                    val isOp = senderPrefix == "@" || senderPrefix == "&" || senderPrefix == "~"
                    DropdownMenuItem(
                        text = { Text(if (isOp) Localizer.getString("deop_user", lang) else Localizer.getString("op_user", lang)) },
                        onClick = { 
                            showMenu = false
                            viewModel.setOp(serverId, msg.target, msg.sender, !isOp)
                        }
                    )

                    val banMask = senderInfo?.hostmask?.let { "*!*@${it.substringAfter("@")}" } ?: "${msg.sender}!*@*"
                    val isActuallyBanned = banList.contains(banMask) || banList.any { it.contains(msg.sender) }
                    
                    DropdownMenuItem(
                        text = { Text(if (isActuallyBanned) "Unban ${msg.sender}" else Localizer.getString("ban_user", lang), color = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            showMenu = false
                            viewModel.banUser(serverId, msg.target, banMask, !isActuallyBanned)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Localizer.getString("kick_user", lang) + " ${msg.sender}", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onAction("/KICK ${msg.target} ${msg.sender}") }
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

fun stripIrcColors(text: String): String {
    return text.replace(Regex("\u0003\\d{0,2}(,\\d{1,2})?|\u0002|\u001f|\u001d|\u000f|\u0016"), "")
}

val IrcColorMap = mapOf(
    "00" to Color(0xFFFFFFFF), "0" to Color(0xFFFFFFFF),  // White
    "01" to Color(0xFF000000), "1" to Color(0xFF000000),  // Black
    "02" to Color(0xFF00007F), "2" to Color(0xFF00007F),  // Blue
    "03" to Color(0xFF009300), "3" to Color(0xFF009300),  // Green
    "04" to Color(0xFFFF0000), "4" to Color(0xFFFF0000),  // Red
    "05" to Color(0xFF7F0000), "5" to Color(0xFF7F0000),  // Brown
    "06" to Color(0xFF9C009C), "6" to Color(0xFF9C009C),  // Purple
    "07" to Color(0xFFFC7F00), "7" to Color(0xFFFC7F00),  // Orange
    "08" to Color(0xFFFFFF00), "8" to Color(0xFFFFFF00),  // Yellow
    "09" to Color(0xFF00FC00), "9" to Color(0xFF00FC00),  // Light Green
    "10" to Color(0xFF009393),                          // Teal
    "11" to Color(0xFF00FFFF),                          // Cyan
    "12" to Color(0xFF0000FC),                          // Light Blue
    "13" to Color(0xFFFF00FF),                          // Pink
    "14" to Color(0xFF7F7F7F),                          // Grey
    "15" to Color(0xFFD2D2D2)                           // Light Grey
)

fun parseIrcColors(text: String, settings: com.personal.ircclient.data.local.entities.SettingsEntity? = null): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var currentColor: Color? = null
        var currentBg: Color? = null
        
        val urlRegex = Regex("(https?://[\\w\\d:#@%/;$()~_?\\+-=\\.&]+)", RegexOption.IGNORE_CASE)

        while (i < text.length) {
            val char = text[i]
            when (char) {
                '\u0002' -> { isBold = !isBold; i++ }
                '\u001d' -> { isItalic = !isItalic; i++ }
                '\u001f' -> { isUnderline = !isUnderline; i++ }
                '\u000f' -> { 
                    isBold = false; isItalic = false; isUnderline = false
                    currentColor = null; currentBg = null; i++ 
                }
                '\u0003' -> {
                    i++
                    val match = Regex("^(\\d{1,2})(,(\\d{1,2}))?").find(text.substring(i))
                    if (match != null) {
                        val fgCode = match.groupValues[1]
                        val bgCode = match.groupValues[3]
                        currentColor = IrcColorMap[fgCode.padStart(2, '0')]
                        if (bgCode.isNotEmpty()) currentBg = IrcColorMap[bgCode.padStart(2, '0')]
                        i += match.value.length
                    } else {
                        currentColor = null; currentBg = null
                    }
                }
                else -> {
                    // Check for URL at current position
                    val urlMatch = urlRegex.find(text.substring(i))
                    if (urlMatch != null && urlMatch.range.start == 0) {
                        val url = urlMatch.value
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(color = Color(0xFF2196F3), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                            append(url)
                        }
                        pop()
                        i += url.length
                    } else {
                        withStyle(
                            SpanStyle(
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                                color = currentColor ?: Color.Unspecified,
                                background = currentBg ?: Color.Transparent
                            )
                        ) {
                            append(char)
                        }
                        i++
                    }
                }
            }
        }
    }
}
