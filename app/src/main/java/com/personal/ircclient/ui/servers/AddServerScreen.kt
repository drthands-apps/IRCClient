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
fun AddServerScreen(
    onSave: (ServerEntity) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("6667") }
    var nickname by remember { mutableStateOf("") }
    var useSsl by remember { mutableStateOf(false) }
    var encoding by remember { mutableStateOf("UTF-8") }
    val encodings = listOf("UTF-8", "ISO-8859-1", "Windows-1252", "CP1251", "ISO-8859-15")
    var expandedEncoding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Server") },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Network Name (e.g. Freenode)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server Host") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
                Text("Use SSL/TLS")
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
                            name = name,
                            host = host,
                            port = port.toIntOrNull() ?: 6667,
                            nickname = nickname,
                            useSsl = useSsl,
                            encoding = encoding
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && host.isNotBlank() && nickname.isNotBlank()
            ) {
                Text("Save Server")
            }
        }
    }
}
