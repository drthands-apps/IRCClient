package com.personal.ircclient.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import com.personal.ircclient.ui.chats.ChatsViewModel

@Composable
fun SettingsScreen(viewModel: ChatsViewModel) {
    val context = LocalContext.current

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
            SettingsItem(
                title = "Theme",
                subtitle = "System Default",
                icon = Icons.Default.Palette,
                onClick = { /* TODO */ }
            )
        }

        SettingsSection(title = "Network & Encoding") {
            SettingsItem(
                title = "Global Default Encoding",
                subtitle = "UTF-8",
                icon = Icons.Default.Language,
                onClick = { /* TODO */ }
            )
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
        }

        SettingsSection(title = "Privacy & Security") {
            SettingsItem(
                title = "Encryption",
                subtitle = "Manage keys and secure chats",
                icon = Icons.Default.Security,
                onClick = { /* TODO */ }
            )
        }

        SettingsSection(title = "Application") {
            SettingsItem(
                title = "Exit Application",
                subtitle = "Close and disconnect all servers",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = {
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
