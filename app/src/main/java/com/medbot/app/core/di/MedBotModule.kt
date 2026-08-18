package com.medbot.app.core.di

import android.app.Application
import com.medbot.app.core.datastore.UserPreferencesManager
import com.medbot.app.data.ai.LlmInferenceEngine
import com.medbot.app.data.download.ModelDownloadManager
import com.medbot.app.data.local.database.MedBotDatabase
import com.medbot.app.data.rag.RagOrchestrator
import com.medbot.app.data.repository.ChatRepositoryImpl
import com.medbot.app.data.repository.DrugRepositoryImpl
import com.medbot.app.data.repository.HealthToolsRepositoryImpl
import com.medbot.app.data.repository.ModelRepositoryImpl
import com.medbot.app.data.repository.RagRepositoryImpl
import com.medbot.app.data.repository.SkinRepositoryImpl
import com.medbot.app.data.vision.SkinDiagnosisEngine
import com.medbot.app.domain.agents.TriageOrchestrator
import com.medbot.app.domain.repository.ChatRepository
import com.medbot.app.domain.repository.DrugRepository
import com.medbot.app.domain.repository.HealthToolsRepository
import com.medbot.app.domain.repository.ModelRepository
import com.medbot.app.domain.repository.RagRepository
import com.medbot.app.domain.repository.SkinRepository
import com.medbot.app.domain.repository.UserPreferencesRepository
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
    fun provideDownloadManager(application: Application): ModelDownloadManager =
        ModelDownloadManager(application)

    @Provides
    @Singleton
    fun provideRagOrchestrator(database: MedBotDatabase): RagOrchestrator =
        RagOrchestrator(database.ragDao())

    @Provides
    @Singleton
    fun provideSkinDiagnosisEngine(): SkinDiagnosisEngine = SkinDiagnosisEngine()

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
    fun provideRagRepository(orchestrator: RagOrchestrator): RagRepository =
        RagRepositoryImpl(orchestrator)

    @Provides
    @Singleton
    fun provideSkinRepository(database: MedBotDatabase): SkinRepository =
        SkinRepositoryImpl(database.skinDao())

    @Provides
    @Singleton
    fun provideDrugRepository(database: MedBotDatabase): DrugRepository =
        DrugRepositoryImpl(database.drugDao())

    @Provides
    @Singleton
    fun provideHealthToolsRepository(database: MedBotDatabase): HealthToolsRepository =
        HealthToolsRepositoryImpl(database.healthToolsDao())

    @Provides
    fun provideSendMessageUseCase(
        chatRepository: ChatRepository,
        ragRepository: RagRepository,
        preferences: UserPreferencesRepository,
        llmEngine: LlmInferenceEngine
    ): SendMessageUseCase = SendMessageUseCase(
        chatRepository = chatRepository,
        ragRepository = ragRepository,
        userPrefsRepository = preferences,
        llmEngine = llmEngine,
        triageOrchestrator = TriageOrchestrator()
    )

    @Provides
    fun provideIngestUseCase(ragRepository: RagRepository): IngestSafDocumentsUseCase =
        IngestSafDocumentsUseCase(ragRepository)

    @Provides
    fun provideAnalyzeSkinUseCase(
        skinRepository: SkinRepository,
        engine: SkinDiagnosisEngine
    ): AnalyzeSkinUseCase = AnalyzeSkinUseCase(skinRepository, engine)

    @Provides
    fun provideDrugInteractionsUseCase(drugRepository: DrugRepository): CheckDrugInteractionsUseCase =
        CheckDrugInteractionsUseCase(drugRepository)
}
