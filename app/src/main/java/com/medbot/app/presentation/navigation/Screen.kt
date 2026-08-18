package com.medbot.app.presentation.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Dashboard")
    object Chat : Screen("chat/{agentId}", "Konsultasi") {
        fun createRoute(agentId: String) = "chat/$agentId"
    }
    object SkinLineage : Screen("skin_lineage", "Kulit & Luka")
    object Tools : Screen("tools", "Perkakas")
    object ModelManager : Screen("model_manager", "Model AI")
    object KnowledgeBase : Screen("knowledge_base", "Dokumen Medis")
}
