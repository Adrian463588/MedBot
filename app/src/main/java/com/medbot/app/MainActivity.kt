package com.medbot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.AndroidEntryPoint
import com.medbot.app.core.designsystem.theme.MedBotTheme
import com.medbot.app.presentation.navigation.MedBotNavigation
import com.medbot.app.presentation.navigation.MedBotRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestedRoute = mutableStateOf(MedBotRoute.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedRoute.value = routeFromIntent(intent)
        enableEdgeToEdge()

        setContent {
            MedBotTheme {
                MedBotNavigation(startDestination = requestedRoute.value)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute.value = routeFromIntent(intent)
    }

    private fun routeFromIntent(intent: android.content.Intent?): String =
        if (intent?.getBooleanExtra(EXTRA_OPEN_MODEL_MANAGER, false) == true) {
            MedBotRoute.MODELS
        } else {
            MedBotRoute.HOME
        }

    companion object {
        const val EXTRA_OPEN_MODEL_MANAGER = "com.medbot.app.extra.OPEN_MODEL_MANAGER"
    }
}
