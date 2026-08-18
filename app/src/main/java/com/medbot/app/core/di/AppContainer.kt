package com.medbot.app.core.di

import android.content.Context
import com.medbot.app.core.datastore.UserPreferencesManager
import com.medbot.app.data.ai.LlmInferenceEngine
import com.medbot.app.data.download.ModelDownloadManager
import com.medbot.app.data.local.database.MedBotDatabase
import com.medbot.app.data.rag.RagOrchestrator
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
        RagOrchestrator(database.ragDao())
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
        RagRepositoryImpl(ragOrchestrator)
    }

    val skinRepository: SkinRepository by lazy {
        SkinRepositoryImpl(database.skinDao())
    }

    val drugRepository: DrugRepository by lazy {
        DrugRepositoryImpl(database.drugDao())
    }

    val healthToolsRepository: HealthToolsRepository by lazy {
        HealthToolsRepositoryImpl(database.healthToolsDao())
    }

    // UseCases
    val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(
            chatRepository = chatRepository,
            ragRepository = ragRepository,
            userPrefsRepository = userPreferencesRepository,
            llmEngine = llmInferenceEngine
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
