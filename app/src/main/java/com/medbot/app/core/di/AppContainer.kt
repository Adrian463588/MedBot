package com.medbot.app.core.di

import android.content.Context
import com.medbot.app.core.datastore.UserPreferencesManager
import com.medbot.app.data.ai.LlmInferenceEngine
import com.medbot.app.data.download.ModelDownloadManager
import com.medbot.app.data.local.database.MedBotDatabase
import com.medbot.app.data.rag.RagOrchestrator
import com.medbot.app.data.rag.LocalEmbedder
import com.medbot.app.data.rag.BundledKnowledgeSeeder
import com.medbot.app.data.rag.RagClinicalEvidenceRepository
import com.medbot.app.data.online.AndroidOnlineEvidenceRepository
import com.medbot.app.data.repository.*
import com.medbot.app.data.vision.SkinDiagnosisEngine
import com.medbot.app.domain.repository.*
import com.medbot.app.domain.usecase.*

class AppContainer(private val context: Context) {

    val database: MedBotDatabase by lazy {
        MedBotDatabase.getDatabase(context)
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesManager(context)
    }

    val llmInferenceEngine: LlmInferenceEngine by lazy {
        LlmInferenceEngine(context)
    }

    val modelDownloadManager: ModelDownloadManager by lazy {
        ModelDownloadManager(context)
    }

    val ragOrchestrator: RagOrchestrator by lazy {
        RagOrchestrator(ragDao = database.ragDao(), embedder = LocalEmbedder(context.applicationContext))
    }

    val bundledKnowledgeSeeder: BundledKnowledgeSeeder by lazy {
        BundledKnowledgeSeeder(context.applicationContext, ragOrchestrator)
    }

    val skinDiagnosisEngine: SkinDiagnosisEngine by lazy {
        SkinDiagnosisEngine()
    }

    // Repositories
    val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(database.chatDao())
    }

    val modelRepository: ModelRepository by lazy {
        ModelRepositoryImpl(modelDownloadManager, llmInferenceEngine)
    }

    val ragRepository: RagRepository by lazy {
        RagRepositoryImpl(ragOrchestrator, bundledKnowledgeSeeder)
    }

    val clinicalEvidenceRepository: ClinicalEvidenceRepository by lazy {
        RagClinicalEvidenceRepository(ragRepository)
    }

    val onlineEvidenceRepository: OnlineEvidenceRepository by lazy {
        AndroidOnlineEvidenceRepository(context.applicationContext)
    }

    val skinRepository: SkinRepository by lazy {
        SkinRepositoryImpl(database.skinDao())
    }

    val drugRepository: DrugRepository by lazy {
        val repo = DrugRepositoryImpl(context, database.drugDao())
        com.medbot.app.domain.agents.tools.ToolRegistry.registerDrugRepository(repo)
        repo
    }

    val healthToolsRepository: HealthToolsRepository by lazy {
        HealthToolsRepositoryImpl(database.healthToolsDao())
    }

    // UseCases
    val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(
            chatRepository = chatRepository,
            ragRepository = ragRepository,
            clinicalEvidenceRepository = clinicalEvidenceRepository,
            llmEngine = llmInferenceEngine,
            onlineEvidenceRepository = onlineEvidenceRepository
        )
    }

    val ingestSafDocumentsUseCase: IngestSafDocumentsUseCase by lazy {
        IngestSafDocumentsUseCase(ragRepository)
    }

    val analyzeSkinUseCase: AnalyzeSkinUseCase by lazy {
        AnalyzeSkinUseCase(skinRepository, skinDiagnosisEngine)
    }

    val checkDrugInteractionsUseCase: CheckDrugInteractionsUseCase by lazy {
        CheckDrugInteractionsUseCase(drugRepository)
    }
}
