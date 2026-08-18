package com.medbot.app.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.medbot.app.core.designsystem.components.MedBotBottomBar
import com.medbot.app.core.di.AppContainer
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
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val bottomBarRoutes = listOf("home", "chat", "skin_lineage", "knowledge", "tools")
    val shouldShowBottomBar = bottomBarRoutes.any { currentRoute.startsWith(it) }

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                MedBotBottomBar(
                    currentRoute = currentRoute.substringBefore("?"),
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
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable("home") {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        modelRepository = appContainer.modelRepository,
                        ragRepository = appContainer.ragRepository,
                        userPreferencesRepository = appContainer.userPreferencesRepository,
                        chatRepository = appContainer.chatRepository
                    )
                )
                HomeScreen(
                    viewModel = homeViewModel,
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
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(
                        chatRepository = appContainer.chatRepository,
                        sendMessageUseCase = appContainer.sendMessageUseCase,
                        userPreferencesRepository = appContainer.userPreferencesRepository
                    )
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    initialSessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPersona = { navController.navigate("persona") }
                )
            }

            composable("skin_lineage") {
                val skinViewModel: SkinViewModel = viewModel(
                    factory = SkinViewModel.Factory(
                        skinRepository = appContainer.skinRepository,
                        analyzeSkinUseCase = appContainer.analyzeSkinUseCase
                    )
                )
                SkinLineageScreen(
                    viewModel = skinViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScan = { navController.navigate("skin_scan") }
                )
            }

            composable("skin_scan") {
                val skinViewModel: SkinViewModel = viewModel(
                    factory = SkinViewModel.Factory(
                        skinRepository = appContainer.skinRepository,
                        analyzeSkinUseCase = appContainer.analyzeSkinUseCase
                    )
                )
                SkinScanScreen(
                    viewModel = skinViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLineage = { navController.navigate("skin_lineage") }
                )
            }

            composable("knowledge") {
                val knowledgeViewModel: KnowledgeViewModel = viewModel(
                    factory = KnowledgeViewModel.Factory(
                        ragRepository = appContainer.ragRepository,
                        ingestSafDocumentsUseCase = appContainer.ingestSafDocumentsUseCase
                    )
                )
                KnowledgeBaseScreen(
                    viewModel = knowledgeViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("models") {
                val modelViewModel: ModelViewModel = viewModel(
                    factory = ModelViewModel.Factory(
                        modelRepository = appContainer.modelRepository,
                        userPreferencesRepository = appContainer.userPreferencesRepository
                    )
                )
                ModelManagerScreen(
                    viewModel = modelViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("persona") {
                val personaViewModel: PersonaViewModel = viewModel(
                    factory = PersonaViewModel.Factory(
                        userPreferencesRepository = appContainer.userPreferencesRepository
                    )
                )
                PersonaConfigScreen(
                    viewModel = personaViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("tools") {
                val toolsViewModel: ToolsViewModel = viewModel(
                    factory = ToolsViewModel.Factory(
                        drugRepository = appContainer.drugRepository,
                        healthToolsRepository = appContainer.healthToolsRepository
                    )
                )
                HealthToolsScreen(
                    viewModel = toolsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
