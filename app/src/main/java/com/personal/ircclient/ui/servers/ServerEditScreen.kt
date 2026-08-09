package com.personal.ircclient.ui.servers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.ircclient.data.local.entities.ServerEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    serverId: Long?,
    viewModel: ServersViewModel,
    onSave: (ServerEntity) -> Unit,
    onBack: () -> Unit
) {
    val servers by viewModel.servers.collectAsState()
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
    
    // SASL
    var useSasl by remember { mutableStateOf(false) }
    var saslUsername by remember { mutableStateOf("") }
    var saslPassword by remember { mutableStateOf("") }
    
    // Bouncer
    var useBouncer by remember { mutableStateOf(false) }
    var bouncerNetwork by remember { mutableStateOf("") }

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
        }
    }

    val encodings = listOf("UTF-8", "ISO-8859-1", "Windows-1252", "CP1251", "ISO-8859-15")
    var expandedEncoding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (serverId == null) "Add Server" else "Edit Server") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            SectionTitle("Connection")
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Network Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Server Host") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
            
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
                Text("Use SSL/TLS")
            }
            if (!useSsl) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = allowPlainText, onCheckedChange = { allowPlainText = it })
                    Text("Allow Plain Text Connection")
                }
            }

            SectionTitle("Identity")
            OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = altNickname, onValueChange = { altNickname = it }, label = { Text("Alternative Nickname") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = generateRandomNick, onCheckedChange = { generateRandomNick = it })
                Text("Generate Random Nick per Connection")
            }
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username (Identity)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = realName, onValueChange = { realName = it }, label = { Text("Real Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Server Password (optional)") }, modifier = Modifier.fillMaxWidth())

            SectionTitle("Advanced - SASL")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = useSasl, onCheckedChange = { useSasl = it })
                Spacer(Modifier.width(8.dp))
                Text("Use SASL Authentication")
            }
            if (useSasl) {
                OutlinedTextField(value = saslUsername, onValueChange = { saslUsername = it }, label = { Text("SASL Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = saslPassword, onValueChange = { saslPassword = it }, label = { Text("SASL Password") }, modifier = Modifier.fillMaxWidth())
            }

            SectionTitle("Advanced - Bouncer")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = useBouncer, onCheckedChange = { useBouncer = it })
                Spacer(Modifier.width(8.dp))
                Text("Connection through Bouncer")
            }
            if (useBouncer) {
                OutlinedTextField(value = bouncerNetwork, onValueChange = { bouncerNetwork = it }, label = { Text("Bouncer Network/Tag") }, modifier = Modifier.fillMaxWidth())
            }

            SectionTitle("Preferences")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isAutoConnect, onCheckedChange = { isAutoConnect = it })
                Text("Auto-Connect on App Start")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = reconnectOpenChannels, onCheckedChange = { reconnectOpenChannels = it })
                Text("Auto-Join Open Channels on Connect")
            }

            ExposedDropdownMenuBox(
                expanded = expandedEncoding,
                onExpandedChange = { expandedEncoding = !expandedEncoding }
            ) {
                OutlinedTextField(
                    value = encoding,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Encoding / Alphabet") },
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
                            bouncerNetwork = bouncerNetwork.ifBlank { null }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                enabled = name.isNotBlank() && host.isNotBlank() && nickname.isNotBlank()
            ) {
                Text(if (serverId == null) "Add Server" else "Save Changes")
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
