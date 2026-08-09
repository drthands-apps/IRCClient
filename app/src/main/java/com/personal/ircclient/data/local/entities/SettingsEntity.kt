package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0, // Single row
    val showEventsInRoom: Boolean = true,
    val isTtsActive: Boolean = false,
    val soundOnFriendJoin: Boolean = true,
    val soundOnPrivateMessage: Boolean = true,
    val soundOnBan: Boolean = true,
    val soundOnMention: Boolean = true,
    val soundOnNotice: Boolean = true,
    val joinDisplayMode: String = "ROOM",
    val partDisplayMode: String = "ROOM",
    val quitDisplayMode: String = "ROOM",
    val nickChangeDisplayMode: String = "ROOM",
    val kickDisplayMode: String = "ROOM",
    val banDisplayMode: String = "ROOM",
    
    // New Search Settings
    val defaultSearchEngine: String = "https://www.google.com/search?q=",
    
    // Security & Privacy
    val useProxy: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    val proxyType: String = "SOCKS", // SOCKS or HTTP
    val customUserAgent: String = "FenixIRC/0.1",
    val openLinksExternally: Boolean = true,
    val preferredBrowser: String = "SYSTEM_DEFAULT",
    val runInBackground: Boolean = false,
    val enableFriendNotify: Boolean = true,
    
    // Preview Settings
    val showLinkPreviews: Boolean = false,
    val autoLoadImages: Boolean = false,
    
    // Appearance
    val enableIrcColors: Boolean = true,
    val themeName: String = "LIGHT", // LIGHT, DARK, OLED
    val ownMessageColor: Long = 0xFFBB86FC,
    val otherBubbleColor: Long = 0xFFE1D5FE,
    val ownBubbleColor: Long = 0xFFFFFFFF,
    val useHighContrast: Boolean = false,
    
    // Privacy Extra
    val allowPrivateOnlyFromFriends: Boolean = false,
    val autoResponseForBlockedPv: String = "I only accept private messages from friends.",
    val defaultAwayMessage: String = "I am away from my keyboard.",
    val defaultBackMessage: String = "I am back."
)
