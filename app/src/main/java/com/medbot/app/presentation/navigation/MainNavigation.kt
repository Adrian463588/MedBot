package com.medbot.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medbot.app.presentation.home.HomeScreen
import com.medbot.app.presentation.chat.ChatScreen
import com.medbot.app.presentation.skin.SkinLineageScreen
import com.medbot.app.presentation.tools.ToolsScreen
import com.medbot.app.presentation.models.ModelManagerScreen
import com.medbot.app.presentation.knowledge.KnowledgeBaseScreen

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavigationItem(Screen.Home, Icons.Filled.Home),
        NavigationItem(Screen.SkinLineage, Icons.Filled.Face),
        NavigationItem(Screen.Tools, Icons.Filled.Build),
        NavigationItem(Screen.ModelManager, Icons.Filled.Settings)
    )

    Scaffold(
        bottomBar = {
            val isBottomBarVisible = items.any { it.screen.route == currentDestination?.route }
            if (isBottomBarVisible) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.screen.title) },
                            label = { Text(item.screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.SkinLineage.route) { SkinLineageScreen(navController) }
            composable(Screen.Tools.route) { ToolsScreen(navController) }
            composable(Screen.ModelManager.route) { ModelManagerScreen(navController) }
            composable(Screen.KnowledgeBase.route) { KnowledgeBaseScreen(navController) }
            composable(Screen.Chat.route) { backStackEntry ->
                val agentId = backStackEntry.arguments?.getString("agentId") ?: "orchestrator"
                ChatScreen(navController, agentId)
            }
        }
    }
}

data class NavigationItem(val screen: Screen, val icon: androidx.compose.ui.graphics.vector.ImageVector)
