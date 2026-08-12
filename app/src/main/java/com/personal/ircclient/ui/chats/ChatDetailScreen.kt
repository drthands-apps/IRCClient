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
import com.personal.ircclient.BuildConfig
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
import kotlinx.coroutines.Dispatchers

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
    val messages by remember(serverId, target) { viewModel.getMessages(serverId, target) }.collectAsState(initial = emptyList())
    val user by remember(serverId, target) { viewModel.getUser(serverId, target) }.collectAsState(initial = null)
    val channelUsers by remember(serverId, target) { viewModel.getChannelUsersWithInfo(serverId, target) }.collectAsState(initial = emptyList())
    val activeTargets by viewModel.activeTargets.collectAsState()
    val currentTargetInfo = activeTargets.find { it.target == target && it.serverId == serverId }
    val myNick by viewModel.getCurrentNickname(serverId).collectAsState()
    val amIOp by viewModel.isUserOp(serverId, target, myNick).collectAsState(initial = false)
    val friends by remember(serverId) { viewModel.getFriends(serverId) }.collectAsState(initial = emptySet())
    val banList by viewModel.getBanList(serverId, target).collectAsState(initial = emptySet())

    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    LaunchedEffect(serverId, target, amIOp) {
        viewModel.clearUnreadCount(serverId, target)
        if (target.startsWith("#") && amIOp) {
            viewModel.refreshBanList(serverId, target)
        }
    }

    val contextAndroid = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val isStatus = target == "Status"
    val isChannel = target.startsWith("#")
    val isUser = !isStatus && !isChannel
    val isPro = com.personal.ircclient.BuildConfig.FLAVOR == "pro" || 
                contextAndroid.packageName.contains(".pro", ignoreCase = true)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val recorder = remember { AudioRecorder(contextAndroid) }
    var isRecording by remember { mutableStateOf(false) }

    DisposableEffect(serverId, target) {
        onDispose {
            if (isRecording) {
                recorder.stopRecording()
            }
            viewModel.onLeaveChat(serverId)
        }
    }
    
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
    var showMultimediaWarning by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var showAsciiSelector by remember { mutableStateOf<String?>(null) } // target user nick
    var searchQuery by remember { mutableStateOf("") }
    val asciiArtItems by viewModel.asciiArt.collectAsState()

    if (isProcessingMedia) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(Localizer.getString("processing", lang)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(Localizer.getString("waiting_media", lang))
                }
            },
            confirmButton = { }
        )
    }

    if (showMultimediaWarning != null) {
        AlertDialog(
            onDismissRequest = { showMultimediaWarning = null },
            title = { Text("Compatibility Warning") },
            text = { Text("To view images, audio, or files correctly, the recipient must also be using FenixIRC. Standard IRC clients will only see a text link. Do you want to proceed?") },
            confirmButton = {
                Button(onClick = { 
                    showMultimediaWarning?.invoke()
                    showMultimediaWarning = null 
                }) { Text("Proceed") }
            },
            dismissButton = {
                TextButton(onClick = { showMultimediaWarning = null }) { Text(Localizer.getString("cancel", lang)) }
            }
        )
    }

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
                                    if (showAsciiSelector != target) {
                                        onNavigateToChat(serverId, showAsciiSelector!!)
                                    }
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
            isProcessingMedia = true
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = contextAndroid.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val watermarked = ImageUtils.addWatermark(bitmap, "IRCClient Secure")
                    
                    if (isUser && isPro) {
                        val tempFile = java.io.File(contextAndroid.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                        val fos = java.io.FileOutputStream(tempFile)
                        watermarked.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, fos)
                        fos.close()
                        
                        FileUploader.uploadFile(tempFile) { url ->
                            scope.launch(Dispatchers.Main) {
                                if (url != null) {
                                    mediaToSend = MessageType.IMAGE to url
                                    showTtlDialog = true
                                }
                                isProcessingMedia = false
                            }
                        }
                    } else {
                        scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                }
            }
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isProcessingMedia = true
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = contextAndroid.contentResolver.openInputStream(it)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    
                    if (isUser && isPro) {
                        val tempFile = java.io.File(contextAndroid.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                        val fos = java.io.FileOutputStream(tempFile)
                        fos.write(bytes)
                        fos.close()
                        
                        FileUploader.uploadFile(tempFile) { url ->
                            scope.launch(Dispatchers.Main) {
                                if (url != null) {
                                    mediaToSend = MessageType.VOICE to url
                                    showTtlDialog = true
                                }
                                isProcessingMedia = false
                            }
                        }
                    } else {
                        scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                }
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isProcessingMedia = true
            scope.launch(Dispatchers.IO) {
                try {
                    val cursor = contextAndroid.contentResolver.query(it, null, null, null, null)
                    val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor?.moveToFirst()
                    val fileName = cursor?.getString(nameIndex ?: -1) ?: "file_${System.currentTimeMillis()}"
                    cursor?.close()

                    val inputStream = contextAndroid.contentResolver.openInputStream(it)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    
                    if (isUser && isPro) {
                        val tempFile = java.io.File(contextAndroid.cacheDir, fileName)
                        val fos = java.io.FileOutputStream(tempFile)
                        fos.write(bytes)
                        fos.close()
                        
                        FileUploader.uploadFile(tempFile) { url ->
                            scope.launch(Dispatchers.Main) {
                                if (url != null) {
                                    mediaToSend = MessageType.FILE to url
                                    showTtlDialog = true
                                }
                                isProcessingMedia = false
                            }
                        }
                    } else {
                        scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "${Localizer.getString("total_users", lang)}: ${channelUsers.size}", 
                    modifier = Modifier.padding(16.dp), 
                    style = MaterialTheme.typography.titleMedium
                )
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
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFF4500), modifier = Modifier.size(14.dp))
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
                                            text = { Text("Send Art/Phrase (PV)") },
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
                        if (isStatus) {
                            var showStatusActions by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showStatusActions = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }
                                DropdownMenu(expanded = showStatusActions, onDismissRequest = { showStatusActions = false }) {
                                    val statusCommands = listOf(
                                        "LIST" to Localizer.getString("discover", lang),
                                        "LUSERS" to "List Users",
                                        "MOTD" to "MOTD",
                                        "INFO" to "Server Info",
                                        "ADMIN" to "Admin Info",
                                        "LINKS" to "Server Links",
                                        "PING" to "Ping Server",
                                        "HELP" to "Help"
                                    )
                                    statusCommands.forEach { (cmd, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                showStatusActions = false
                                                if (cmd == "LIST") onNavigateToDiscovery(serverId)
                                                else handleSend("/$cmd")
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(Localizer.getString("clear_history", lang)) },
                                        onClick = { showStatusActions = false; showClearConfirm = true }
                                    )
                                }
                            }
                        } else if (!isStatus) {
                            if (isChannel) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.People, contentDescription = null)
                                }
                            }
                            if (isUser && user?.encryptionKey == null && isPro) {
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
                                    if (isChannel) {
                                        DropdownMenuItem(
                                            text = { Text("Send Art/Phrase (General)") },
                                            leadingIcon = { Icon(Icons.Default.ArtTrack, null) },
                                            onClick = { 
                                                showMoreActions = false
                                                showAsciiSelector = target
                                            }
                                        )
                                        HorizontalDivider()
                                    }
                                    
                                    val availableCommands = viewModel.getAvailableCommands(serverId, target, amIOp)
                                    availableCommands.forEach { cmd ->
                                        if (cmd == "PART" || cmd == "QUIT") {
                                            HorizontalDivider()
                                        }
                                        
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
                                    
                                    HorizontalDivider()

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
                    if (isUser && user?.secureHandshakeStatus == HandshakeStatus.RECEIVED && isPro) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Localizer.getString("secure_requested", lang), style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { viewModel.acceptSecureChat(serverId, target) }) {
                                    Text(Localizer.getString("accept_generate_key", lang))
                                }
                            }
                        }
                    } else if (isUser && user?.secureHandshakeStatus == HandshakeStatus.REQUESTED && isPro) {
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
                                Text(Localizer.getString("waiting_secure_accept", lang), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
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
                            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                            val rawInputText = textFieldValue.text
                            val lastWord = rawInputText.substringBeforeLast(" ", "").let { 
                                rawInputText.substring(it.length).trim()
                            }
                            if ((lastWord.startsWith("@") || lastWord.startsWith(":")) && lastWord.length > 1) {
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
                                leadingIcon = if (!isStatus && isPro) {
                                    {
                                        Box {
                                            IconButton(onClick = { 
                                                showMultimediaWarning = { showAttachMenu = true }
                                            }) {
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
                                                DropdownMenuItem(
                                                    text = { Text(Localizer.getString("attach_file", lang)) },
                                                    leadingIcon = { Icon(Icons.Default.AttachFile, null) },
                                                    onClick = { showAttachMenu = false; fileLauncher.launch("*/*") }
                                                )
                                            }
                                        }
                                    }
                                } else null
                            )

                            if (showNickSuggestions) {
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(y = (-60).dp)
                                        .fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Card {
                                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                            items(suggestions) { nick ->
                                                ListItem(
                                                    headlineContent = { Text(nick) },
                                                    modifier = Modifier.clickable {
                                                        val rawInputText = textFieldValue.text
                                                        val lastAt = rawInputText.lastIndexOf("@")
                                                        val lastColon = rawInputText.lastIndexOf(":")
                                                        val lastWordStart = if (lastAt >= 0 || lastColon >= 0) maxOf(lastAt, lastColon) else -1
                                                        
                                                        if (lastWordStart != -1) {
                                                            val isStartOfLine = lastWordStart == 0
                                                            val replacement = if (isStartOfLine) "$nick: " else "$nick "
                                                            val newText = rawInputText.substring(0, lastWordStart) + replacement
                                                            textFieldValue = TextFieldValue(newText, TextRange(newText.length))
                                                        }
                                                        showNickSuggestions = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!isStatus && isPro) {
                            IconButton(onClick = { 
                                if (isRecording) {
                                    isProcessingMedia = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val file = recorder.stopRecording()
                                            if (file != null) {
                                                // Upload recorded audio instead of sending Base64 (to avoid 512-byte IRC limit)
                                                FileUploader.uploadFile(file) { url ->
                                                    scope.launch(Dispatchers.Main) {
                                                        if (url != null) {
                                                            mediaToSend = MessageType.VOICE to url
                                                            showTtlDialog = true
                                                        }
                                                        isProcessingMedia = false
                                                    }
                                                }
                                            } else {
                                                scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                                            }
                                        } catch (e: Exception) {
                                            scope.launch(Dispatchers.Main) { isProcessingMedia = false }
                                        }
                                    }
                                    isRecording = false
                                } else {
                                    showMultimediaWarning = {
                                        recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
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
            Box(modifier = Modifier.padding(padding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (settings.isRadioPluginEnabled) {
                        val streamUrl = settings.selectedRadioUrl
                        val radioName = settings.selectedRadioName
                        
                        if (streamUrl.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                val isPlaying by RadioPlayer.isPlaying.collectAsState()
                                val currentUrl by RadioPlayer.currentUrl.collectAsState()
                                val isCurrentStation = currentUrl == streamUrl && isPlaying

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
                        items(messages, key = { it.id }) { msg ->
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
                    msg.type == MessageType.BAN -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    isMe -> Color(settings.ownBubbleColor)
                    isStatus || isSystem -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color(settings.otherBubbleColor)
                },
                border = when {
                    msg.type == MessageType.NOTICE -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                    msg.type == MessageType.BAN -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                    isMe -> BorderStroke(1.dp, Color(settings.otherBubbleColor))
                    else -> null
                },
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().clickable { showMenu = true }.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (msg.type == MessageType.NOTICE) {
                            Text(text = "${Localizer.getString("notice", lang)}: ${msg.sender}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                        } else if (!isMe && !isStatus && !isSystem) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (msg.isEncrypted) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Green)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (senderPrefix.isNotEmpty()) "$senderPrefix${msg.sender}" else msg.sender, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Bold
                                )
                                if (isFriend) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFF4500), modifier = Modifier.size(12.dp))
                                }
                            }
                        } else if (isMe) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (msg.isEncrypted) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Green)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(text = myNick, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(settings.ownMessageColor))
                            }
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
                    val isDrawing = (lines.size > 5 && lines.any { it.contains("  ") } && !msg.text.startsWith("WHOIS Results:")) || 
                                    (isMOTD && lines.any { it.contains("  ") } && !msg.text.startsWith("WHOIS Results:"))
                    
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
                                            fontSize = 8.sp,
                                            lineHeight = 9.sp,
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
                        val isUrl = msg.text.startsWith("http", ignoreCase = true)
                        // A catbox ID is alphanumeric, contains a dot, and is short (usually < 15 chars)
                        val isCatboxId = !isUrl && msg.text.contains(".") && msg.text.length < 20 && !msg.text.contains(" ")
                        val finalUrl = if (isCatboxId) "https://files.catbox.moe/${msg.text.trim()}" else msg.text
                        
                        if ((isUrl || isCatboxId) && finalUrl.isNotBlank()) {
                            AsyncImage(
                                model = finalUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .clickable {
                                        LinkHandler.openLink(contextAndroid, finalUrl, settings)
                                    }
                            )
                        } else {
                            // Fallback for Base64 (legacy or direct small images)
                            // Skip if string is suspiciously long to avoid OOM
                            val isLikelyBase64 = msg.text.length < 50000 && !msg.text.contains(" ") && msg.text.length > 20
                            
                            val bitmap = if (isLikelyBase64) {
                                remember(msg.text) {
                                    try {
                                        val bytes = Base64.decode(msg.text.trim(), Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (e: Exception) { null }
                                }
                            } else null

                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(150.dp).padding(top = 8.dp)
                                )
                            } ?: Text(text = "[Image could not be loaded]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    } else if (msg.type == MessageType.VOICE) {
                        val isUrl = msg.text.startsWith("http", ignoreCase = true)
                        val isCatboxId = !isUrl && msg.text.contains(".") && msg.text.length < 20 && !msg.text.contains(" ")
                        val finalUrl = if (isCatboxId) "https://files.catbox.moe/${msg.text.trim()}" else msg.text

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow, 
                                contentDescription = null,
                                modifier = Modifier.clickable {
                                    if (isUrl || isCatboxId) {
                                        LinkHandler.openLink(contextAndroid, finalUrl, settings)
                                    }
                                }
                            )
                            Text(if (isUrl || isCatboxId) Localizer.getString("voice_message_ext", lang) else Localizer.getString("voice_message", lang), style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (msg.type == MessageType.FILE) {
                        val isUrl = msg.text.startsWith("http", ignoreCase = true)
                        val isCatboxId = !isUrl && msg.text.contains(".") && msg.text.length < 20 && !msg.text.contains(" ")
                        val finalUrl = if (isCatboxId) "https://files.catbox.moe/${msg.text.trim()}" else msg.text

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Description, 
                                contentDescription = null,
                                modifier = Modifier.clickable {
                                    if (isUrl || isCatboxId) {
                                        LinkHandler.openLink(contextAndroid, finalUrl, settings)
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(Localizer.getString("file_message", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
                                if (isUrl || isCatboxId) {
                                    LinkHandler.openLink(contextAndroid, finalUrl, settings)
                                }
                            })
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
                    text = { Text("Send Art/Phrase (PV)") },
                    leadingIcon = { Icon(Icons.Default.ArtTrack, null) },
                    onClick = { 
                        showMenu = false
                        onAction("/ART_SELECTOR ${msg.sender}") 
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
                    text = { Text(if (viewModel.isAway) "Set Back" else "Set Away") },
                    onClick = { 
                        showMenu = false
                        onAction(if (viewModel.isAway) "/BACK" else "/AWAY")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Action (/me)") },
                    onClick = { 
                        showMenu = false
                        val cmd = "/ME "
                        onTextChange(TextFieldValue(cmd, TextRange(cmd.length)))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Change Nickname") },
                    onClick = { 
                        showMenu = false
                        val cmd = "/NICK "
                        onTextChange(TextFieldValue(cmd, TextRange(cmd.length)))
                    }
                )
            }
        }
    }
}
