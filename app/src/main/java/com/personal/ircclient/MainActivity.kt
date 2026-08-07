package com.personal.ircclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.personal.ircclient.ui.main.MainScreen
import com.personal.ircclient.ui.theme.IRCClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IRCClientTheme {
                MainScreen()
            }
        }
    }
}
