package com.personal.ircclient.ui.servers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.ircclient.core.utils.Localizer
import com.personal.ircclient.data.local.entities.ServerEntity
import com.personal.ircclient.ui.chats.ChatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    serverId: Long?,
    viewModel: ServersViewModel,
    onSave: (ServerEntity) -> Unit,
    onBack: () -> Unit
) {
    val servers by viewModel.servers.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = null)
    val lang = settings?.language ?: "en"

    val existingServer = remember(serverId, servers) { 
        servers.find { it.id == serverId } 
    }

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("6667") }
    var nickname by remember { mutableStateOf("") }
    var altNickname by remember { mutableStateOf("") }
    var generateRandomNick by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("irc_user") }
    var realName by remember { mutableStateOf("IRC Client User") }
    var password by remember { mutableStateOf("") }
    var useSsl by remember { mutableStateOf(false) }
    var allowPlainText by remember { mutableStateOf(true) }
    var encoding by remember { mutableStateOf("UTF-8") }
    var isAutoConnect by remember { mutableStateOf(false) }
    var reconnectOpenChannels by remember { mutableStateOf(true) }
    
    var useSasl by remember { mutableStateOf(false) }
    var saslUsername by remember { mutableStateOf("") }
    var saslPassword by remember { mutableStateOf("") }
    
    var useBouncer by remember { mutableStateOf(false) }
    var bouncerNetwork by remember { mutableStateOf("") }
    
    var email by remember { mutableStateOf("") }
    var onConnectCommands by remember { mutableStateOf("") }

    LaunchedEffect(existingServer) {
        existingServer?.let {
            name = it.name
            host = it.host
            port = it.port.toString()
            nickname = it.nickname
            altNickname = it.altNickname ?: ""
            generateRandomNick = it.generateRandomNick
            username = it.username
            realName = it.realName
            password = it.password ?: ""
            useSsl = it.useSsl
            allowPlainText = it.allowPlainText
            encoding = it.encoding
            isAutoConnect = it.isAutoConnect
            reconnectOpenChannels = it.reconnectOpenChannels
            useSasl = it.useSasl
            saslUsername = it.saslUsername ?: ""
            saslPassword = it.saslPassword ?: ""
            useBouncer = it.useBouncer
            bouncerNetwork = it.bouncerNetwork ?: ""
            email = it.email ?: ""
            onConnectCommands = it.onConnectCommands ?: ""
        }
    }

    val encodings = listOf("UTF-8", "ISO-8859-1", "Windows-1252", "CP1251", "ISO-8859-15")
    var expandedEncoding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (serverId == null) Localizer.getString("add_server", lang) else Localizer.getString("edit_server", lang)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle(Localizer.getString("connection", lang))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(Localizer.getString("network_name", lang)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(Localizer.getString("server_host", lang)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text(Localizer.getString("port", lang)) }, modifier = Modifier.fillMaxWidth())
            
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
                Text(Localizer.getString("use_ssl", lang))
            }
            if (!useSsl) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = allowPlainText, onCheckedChange = { allowPlainText = it })
                    Text(Localizer.getString("allow_plain_text", lang))
                }
            }

            SectionTitle(Localizer.getString("identity", lang))
            OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text(Localizer.getString("nickname", lang)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = altNickname, onValueChange = { altNickname = it }, label = { Text(Localizer.getString("alt_nickname", lang)) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = generateRandomNick, onCheckedChange = { generateRandomNick = it })
                Text(Localizer.getString("random_nick", lang))
            }
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(Localizer.getString("username", lang)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = realName, onValueChange = { realName = it }, label = { Text(Localizer.getString("real_name", lang)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (for registration)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(Localizer.getString("server_password", lang)) }, modifier = Modifier.fillMaxWidth())

            SectionTitle("Post-Connection Commands")
            OutlinedTextField(
                value = onConnectCommands, 
                onValueChange = { onConnectCommands = it }, 
                label = { Text("Commands (one per line)") }, 
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("/msg NickServ identify pass\n/JOIN #myroom") }
            )

            SectionTitle(Localizer.getString("advanced_sasl", lang))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = useSasl, onCheckedChange = { useSasl = it })
                Spacer(Modifier.width(8.dp))
                Text(Localizer.getString("use_sasl", lang))
            }
            if (useSasl) {
                OutlinedTextField(value = saslUsername, onValueChange = { saslUsername = it }, label = { Text(Localizer.getString("sasl_username", lang)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = saslPassword, onValueChange = { saslPassword = it }, label = { Text(Localizer.getString("sasl_password", lang)) }, modifier = Modifier.fillMaxWidth())
            }

            SectionTitle(Localizer.getString("advanced_bouncer", lang))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = useBouncer, onCheckedChange = { useBouncer = it })
                Spacer(Modifier.width(8.dp))
                Text(Localizer.getString("use_bouncer", lang))
            }
            if (useBouncer) {
                OutlinedTextField(value = bouncerNetwork, onValueChange = { bouncerNetwork = it }, label = { Text(Localizer.getString("bouncer_network", lang)) }, modifier = Modifier.fillMaxWidth())
            }

            SectionTitle(Localizer.getString("preferences", lang))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isAutoConnect, onCheckedChange = { isAutoConnect = it })
                Text(Localizer.getString("auto_connect", lang))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = reconnectOpenChannels, onCheckedChange = { reconnectOpenChannels = it })
                Text(Localizer.getString("reconnect_channels", lang))
            }

            ExposedDropdownMenuBox(
                expanded = expandedEncoding,
                onExpandedChange = { expandedEncoding = !expandedEncoding }
            ) {
                OutlinedTextField(
                    value = encoding,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(Localizer.getString("encoding", lang)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEncoding) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedEncoding,
                    onDismissRequest = { expandedEncoding = false }
                ) {
                    encodings.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                encoding = selectionOption
                                expandedEncoding = false
                            }
                        )
                    }
                }
            }
            
            Button(
                onClick = {
                    onSave(
                        ServerEntity(
                            id = serverId ?: 0,
                            name = name,
                            host = host,
                            port = port.toIntOrNull() ?: 6667,
                            nickname = nickname,
                            altNickname = altNickname.ifBlank { null },
                            generateRandomNick = generateRandomNick,
                            username = username,
                            realName = realName,
                            password = password.ifBlank { null },
                            useSsl = useSsl,
                            allowPlainText = allowPlainText,
                            encoding = encoding,
                            isAutoConnect = isAutoConnect,
                            reconnectOpenChannels = reconnectOpenChannels,
                            useSasl = useSasl,
                            saslUsername = saslUsername.ifBlank { null },
                            saslPassword = saslPassword.ifBlank { null },
                            useBouncer = useBouncer,
                            bouncerNetwork = bouncerNetwork.ifBlank { null },
                            email = email.ifBlank { null },
                            onConnectCommands = onConnectCommands.ifBlank { null }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                enabled = name.isNotBlank() && host.isNotBlank() && nickname.isNotBlank()
            ) {
                Text(if (serverId == null) Localizer.getString("add_server", lang) else Localizer.getString("save_changes", lang))
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}
