package com.medbot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.medbot.app.core.designsystem.theme.MedBotTheme
import com.medbot.app.presentation.navigation.MedBotNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as MedBotApplication).container

        setContent {
            MedBotTheme {
                MedBotNavigation(appContainer = appContainer)
            }
        }
    }
}
