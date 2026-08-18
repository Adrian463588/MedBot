package com.medbot.app.core.di

import android.content.Context
import androidx.room.Room
import com.medbot.app.data.local.MedBotDatabase
import com.medbot.app.data.local.dao.ChatDao
import com.medbot.app.data.local.dao.DrugDao
import com.medbot.app.data.local.dao.RagDao
import com.medbot.app.data.local.dao.SkinDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMedBotDatabase(
        @ApplicationContext context: Context
    ): MedBotDatabase {
        return Room.databaseBuilder(
            context,
            MedBotDatabase::class.java,
            "medbot_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: MedBotDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideRagDao(database: MedBotDatabase): RagDao {
        return database.ragDao()
    }

    @Provides
    @Singleton
    fun provideSkinDao(database: MedBotDatabase): SkinDao {
        return database.skinDao()
    }

    @Provides
    @Singleton
    fun provideDrugDao(database: MedBotDatabase): DrugDao {
        return database.drugDao()
    }
}
