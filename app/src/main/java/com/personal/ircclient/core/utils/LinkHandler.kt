package com.personal.ircclient.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.personal.ircclient.data.local.entities.SettingsEntity

object LinkHandler {
    fun openLink(context: Context, url: String, settings: SettingsEntity) {
        if (!settings.openLinksExternally) return
        
        try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (settings.preferredBrowser != "SYSTEM_DEFAULT") {
                    setPackage(settings.preferredBrowser)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to system default if preferred browser fails
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
}
