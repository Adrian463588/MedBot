package com.medbot.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MedBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
