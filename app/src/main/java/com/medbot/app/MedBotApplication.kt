package com.medbot.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.medbot.app.data.rag.BundledKnowledgeSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class MedBotApplication : Application() {
    @Inject
    lateinit var bundledKnowledgeSeeder: BundledKnowledgeSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        bundledKnowledgeSeeder.start(applicationScope)
    }
}
