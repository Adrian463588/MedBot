package com.medbot.app

import android.app.Application
import com.medbot.app.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedBotApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Pre-initialize database and pre-warm seeder in background
        CoroutineScope(Dispatchers.IO).launch {
            container.database.drugDao().getDrugCount()
        }
    }
}
