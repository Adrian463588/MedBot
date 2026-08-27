package com.medbot.app.core.di

import android.app.Application
import com.medbot.app.core.datastore.UserPreferencesManager
import com.medbot.app.data.ai.LlmInferenceEngine
import com.medbot.app.data.download.ModelDownloadManager
import com.medbot.app.data.local.database.MedBotDatabase
import com.medbot.app.data.rag.RagOrchestrator
import com.medbot.app.data.rag.LocalEmbedder
import com.medbot.app.data.rag.BundledKnowledgeSeeder
import com.medbot.app.data.rag.RagClinicalEvidenceRepository
import com.medbot.app.data.online.AndroidOnlineEvidenceRepository
import com.medbot.app.data.repository.ChatRepositoryImpl
import com.medbot.app.data.repository.DrugRepositoryImpl
import com.medbot.app.data.repository.HealthToolsRepositoryImpl
import com.medbot.app.data.repository.ModelRepositoryImpl
import com.medbot.app.data.repository.RagRepositoryImpl
import com.medbot.app.data.repository.SkinRepositoryImpl
import com.medbot.app.data.vision.SkinDiagnosisEngine
import com.medbot.app.data.vision.AndroidSkinMediaGateway
import com.medbot.app.data.platform.AndroidSafGateway
import com.medbot.app.domain.agents.TriageOrchestrator
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.DrugRepository
import com.medbot.app.domain.repository.HealthToolsRepository
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.repository.SkinRepository
import com.medbot.app.domain.repository.LocalLlmGateway
import com.medbot.app.domain.repository.SkinAnalysisGateway
import com.medbot.app.domain.repository.SkinMediaGateway
import com.medbot.app.domain.repository.SafDocumentGateway
import com.medbot.app.domain.repository.ModelFileGateway
import com.medbot.app.domain.repository.ModelStorageGateway
import com.medbot.app.domain.repository.UserPreferencesRepository
import com.medbot.app.domain.repository.OnlineEvidenceRepository
import com.medbot.app.domain.repository.ClinicalEvidenceRepository
import com.medbot.app.domain.usecase.AnalyzeSkinUseCase
import com.medbot.app.domain.usecase.CheckDrugInteractionsUseCase
import com.medbot.app.domain.usecase.IngestSafDocumentsUseCase
import com.medbot.app.domain.usecase.SendMessageUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Application-scoped composition root for the local-only MedBot graph. */
@Module
@InstallIn(SingletonComponent::class)
object MedBotModule {

    @Provides
    @Singleton
    fun provideDatabase(application: Application): MedBotDatabase =
        MedBotDatabase.getDatabase(application)

    @Provides
    @Singleton
    fun providePreferences(application: Application): UserPreferencesRepository =
        UserPreferencesManager(application)

    @Provides
    @Singleton
    fun provideLlmEngine(application: Application): LlmInferenceEngine =
        LlmInferenceEngine(application)

    @Provides
    @Singleton
    fun provideLocalLlmGateway(engine: LlmInferenceEngine): LocalLlmGateway = engine

    @Provides
    @Singleton
    fun provideDownloadManager(application: Application): ModelDownloadManager =
        ModelDownloadManager(application)

    @Provides
    @Singleton
    fun provideRagOrchestrator(
        application: Application,
        database: MedBotDatabase
    ): RagOrchestrator = RagOrchestrator(
        ragDao = database.ragDao(),
        embedder = LocalEmbedder(application)
    )

    @Provides
    @Singleton
    fun provideBundledKnowledgeSeeder(
        application: Application,
        ragOrchestrator: RagOrchestrator
    ): BundledKnowledgeSeeder = BundledKnowledgeSeeder(application, ragOrchestrator)

    @Provides
    @Singleton
    fun provideSkinDiagnosisEngine(): SkinDiagnosisEngine = SkinDiagnosisEngine()

    @Provides
    @Singleton
    fun provideSkinAnalysisGateway(engine: SkinDiagnosisEngine): SkinAnalysisGateway = engine

    @Provides
    @Singleton
    fun provideSkinMediaGateway(application: Application): SkinMediaGateway =
        AndroidSkinMediaGateway(application)

    @Provides
    @Singleton
    fun provideAndroidSafGateway(application: Application): AndroidSafGateway =
        AndroidSafGateway(application)

    @Provides
    @Singleton
    fun provideSafDocumentGateway(gateway: AndroidSafGateway): SafDocumentGateway = gateway

    @Provides
    @Singleton
    fun provideModelFileGateway(gateway: AndroidSafGateway): ModelFileGateway = gateway

    @Provides
    @Singleton
    fun provideModelStorageGateway(gateway: AndroidSafGateway): ModelStorageGateway = gateway

    @Provides
    @Singleton
    fun provideChatRepository(database: MedBotDatabase): ChatRepository =
        ChatRepositoryImpl(database.chatDao())

    @Provides
    @Singleton
    fun provideModelRepository(
        downloadManager: ModelDownloadManager,
        llmEngine: LlmInferenceEngine
    ): ModelRepository = ModelRepositoryImpl(downloadManager, llmEngine)

    @Provides
    @Singleton
    fun provideRagRepository(
        orchestrator: RagOrchestrator,
        bundledKnowledgeSeeder: BundledKnowledgeSeeder
    ): RagRepository = RagRepositoryImpl(orchestrator, bundledKnowledgeSeeder)

    @Provides
    @Singleton
    fun provideClinicalEvidenceRepository(ragRepository: RagRepository): ClinicalEvidenceRepository =
        RagClinicalEvidenceRepository(ragRepository)

    @Provides
    @Singleton
    fun provideOnlineEvidenceRepository(application: Application): OnlineEvidenceRepository =
        AndroidOnlineEvidenceRepository(application)

    @Provides
    @Singleton
    fun provideSkinRepository(database: MedBotDatabase): SkinRepository =
        SkinRepositoryImpl(database.skinDao())

    @Provides
    @Singleton
    fun provideDrugRepository(
        application: Application,
        database: MedBotDatabase
    ): DrugRepository {
        val repo = DrugRepositoryImpl(application, database.drugDao())
        com.medbot.app.domain.agents.tools.ToolRegistry.registerDrugRepository(repo)
        return repo
    }

    @Provides
    @Singleton
    fun provideHealthToolsRepository(
        database: MedBotDatabase,
        reminderScheduler: com.medbot.app.data.reminder.ReminderScheduler
    ): HealthToolsRepository =
        HealthToolsRepositoryImpl(database.healthToolsDao(), reminderScheduler)

    @Provides
    fun provideSendMessageUseCase(
        chatRepository: ChatRepository,
        ragRepository: RagRepository,
        clinicalEvidenceRepository: ClinicalEvidenceRepository,
        llmEngine: LocalLlmGateway,
        onlineEvidenceRepository: OnlineEvidenceRepository
    ): SendMessageUseCase = SendMessageUseCase(
        chatRepository = chatRepository,
        ragRepository = ragRepository,
        clinicalEvidenceRepository = clinicalEvidenceRepository,
        llmEngine = llmEngine,
        triageOrchestrator = TriageOrchestrator(),
        onlineEvidenceRepository = onlineEvidenceRepository
    )

    @Provides
    fun provideIngestUseCase(ragRepository: RagRepository): IngestSafDocumentsUseCase =
        IngestSafDocumentsUseCase(ragRepository)

    @Provides
    fun provideAnalyzeSkinUseCase(
        skinRepository: SkinRepository,
        engine: SkinAnalysisGateway
    ): AnalyzeSkinUseCase = AnalyzeSkinUseCase(skinRepository, engine)

    @Provides
    fun provideDrugInteractionsUseCase(drugRepository: DrugRepository): CheckDrugInteractionsUseCase =
        CheckDrugInteractionsUseCase(drugRepository)
}
