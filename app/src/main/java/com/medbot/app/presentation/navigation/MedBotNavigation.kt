package com.medbot.app.presentation.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medbot.app.core.designsystem.components.MedBotBottomBar
import com.medbot.app.core.designsystem.components.MedBotNavigationRail
import com.medbot.app.core.designsystem.components.MedBotWindowWidth
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

object MedBotRoute {
    const val HOME = "home"
    const val CHAT = "chat"
    const val SKIN_LINEAGE = "skin_lineage"
    const val SKIN_SCAN = "skin_scan"
    const val KNOWLEDGE = "knowledge"
    const val MODELS = "models"
    const val PERSONA = "persona"
    const val TOOLS = "tools"
}

private val rootRoutes = setOf(
    MedBotRoute.HOME,
    MedBotRoute.SKIN_LINEAGE,
    MedBotRoute.KNOWLEDGE,
    MedBotRoute.TOOLS
)

@Composable
fun MedBotNavigation(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("?") ?: MedBotRoute.HOME

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowWidth = when {
            maxWidth < 600.dp -> MedBotWindowWidth.COMPACT
            maxWidth < 840.dp -> MedBotWindowWidth.MEDIUM
            else -> MedBotWindowWidth.EXPANDED
        }
        val showRootNavigation = currentRoute in rootRoutes
        val showRail = windowWidth != MedBotWindowWidth.COMPACT

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                if (showRootNavigation && !showRail) {
                    MedBotBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { route -> navigateToRoot(navController, route, currentRoute) }
                    )
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (showRootNavigation && showRail) {
                    MedBotNavigationRail(
                        currentRoute = currentRoute,
                        onNavigate = { route -> navigateToRoot(navController, route, currentRoute) }
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = MedBotRoute.HOME,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {
                    composable(MedBotRoute.HOME) {
                        HomeScreen(
                            viewModel = hiltViewModel<HomeViewModel>(),
                            onNavigateToChat = { sessionId ->
                                navigateSingleTop(
                                    navController,
                                    if (sessionId == null) MedBotRoute.CHAT else "${MedBotRoute.CHAT}?sessionId=$sessionId"
                                )
                            },
                            onNavigateToSkinScan = { navigateSingleTop(navController, MedBotRoute.SKIN_SCAN) },
                            onNavigateToKnowledge = { navigateSingleTop(navController, MedBotRoute.KNOWLEDGE) },
                            onNavigateToModels = { navigateSingleTop(navController, MedBotRoute.MODELS) },
                            onNavigateToPersona = { navigateSingleTop(navController, MedBotRoute.PERSONA) },
                            onNavigateToTools = { navigateSingleTop(navController, MedBotRoute.TOOLS) }
                        )
                    }
                    composable(
                        route = "${MedBotRoute.CHAT}?sessionId={sessionId}",
                        arguments = listOf(
                            navArgument("sessionId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { entry ->
                        ChatScreen(
                            viewModel = hiltViewModel<ChatViewModel>(),
                            initialSessionId = entry.arguments?.getString("sessionId"),
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPersona = { navigateSingleTop(navController, MedBotRoute.PERSONA) }
                        )
                    }
                    composable(MedBotRoute.SKIN_LINEAGE) {
                        SkinLineageScreen(
                            viewModel = hiltViewModel<SkinViewModel>(),
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToScan = { navigateSingleTop(navController, MedBotRoute.SKIN_SCAN) }
                        )
                    }
                    composable(MedBotRoute.SKIN_SCAN) {
                        SkinScanScreen(
                            viewModel = hiltViewModel<SkinViewModel>(),
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToLineage = { navigateSingleTop(navController, MedBotRoute.SKIN_LINEAGE) }
                        )
                    }
                    composable(MedBotRoute.KNOWLEDGE) {
                        KnowledgeBaseScreen(
                            viewModel = hiltViewModel<KnowledgeViewModel>(),
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(MedBotRoute.MODELS) {
                        ModelManagerScreen(
                            viewModel = hiltViewModel<ModelViewModel>(),
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(MedBotRoute.PERSONA) {
                        PersonaConfigScreen(
                            viewModel = hiltViewModel<PersonaViewModel>(),
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(MedBotRoute.TOOLS) {
                        HealthToolsScreen(viewModel = hiltViewModel<ToolsViewModel>())
                    }
                }
            }
        }
    }
}

private fun navigateToRoot(
    navController: NavHostController,
    route: String,
    currentRoute: String
) {
    if (route == currentRoute) return
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navigateSingleTop(navController: NavHostController, route: String) {
    navController.navigate(route) { launchSingleTop = true }
}
