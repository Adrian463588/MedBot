package com.medbot.app.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.medbot.app.core.designsystem.components.MedBotBottomBar
import com.medbot.app.presentation.chat.ChatScreen
import com.medbot.app.presentation.chat.ChatViewModel
import com.medbot.app.presentation.home.HomeScreen
import com.medbot.app.presentation.home.HomeViewModel
import com.medbot.app.presentation.knowledge.KnowledgeBaseScreen
import com.medbot.app.presentation.knowledge.KnowledgeViewModel
import com.medbot.app.presentation.models.ModelManagerScreen
import com.medbot.app.presentation.models.ModelViewModel
import com.medbot.app.presentation.persona.PersonaConfigScreen
import com.medbot.app.presentation.persona.PersonaViewModel
import com.medbot.app.presentation.skin.SkinLineageScreen
import com.medbot.app.presentation.skin.SkinScanScreen
import com.medbot.app.presentation.skin.SkinViewModel
import com.medbot.app.presentation.tools.HealthToolsScreen
import com.medbot.app.presentation.tools.ToolsViewModel

@Composable
fun MedBotNavigation(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val bottomBarRoutes = listOf("home", "skin_lineage", "knowledge", "tools")
    val shouldShowBottomBar = bottomBarRoutes.contains(currentRoute)

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                MedBotBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (shouldShowBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = hiltViewModel(),
                    onNavigateToChat = { sessionId ->
                        if (sessionId != null) {
                            navController.navigate("chat?sessionId=$sessionId")
                        } else {
                            navController.navigate("chat")
                        }
                    },
                    onNavigateToSkinScan = { navController.navigate("skin_scan") },
                    onNavigateToKnowledge = { navController.navigate("knowledge") },
                    onNavigateToModels = { navController.navigate("models") },
                    onNavigateToPersona = { navController.navigate("persona") },
                    onNavigateToTools = { navController.navigate("tools") }
                )
            }

            composable(
                route = "chat?sessionId={sessionId}",
                arguments = listOf(navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")
                ChatScreen(
                    viewModel = hiltViewModel(),
                    initialSessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPersona = { navController.navigate("persona") }
                )
            }

            composable("skin_lineage") {
                SkinLineageScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScan = { navController.navigate("skin_scan") }
                )
            }

            composable("skin_scan") {
                SkinScanScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLineage = { navController.navigate("skin_lineage") }
                )
            }

            composable("knowledge") {
                KnowledgeBaseScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("models") {
                ModelManagerScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("persona") {
                PersonaConfigScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("tools") {
                HealthToolsScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
