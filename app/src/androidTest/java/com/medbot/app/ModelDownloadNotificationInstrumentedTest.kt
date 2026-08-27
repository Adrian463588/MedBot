package com.medbot.app

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.medbot.app.data.download.ModelDownloadNotification
import com.medbot.app.data.download.ModelDownloadNotificationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelDownloadNotificationInstrumentedTest {

    @Test
    fun progressNotificationUsesWorkerByteProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val foregroundInfo = ModelDownloadNotification.createForegroundInfo(
            context = context,
            modelId = "notification-test-model",
            displayName = "Verified model",
            bytesDownloaded = 50_000_000L,
            totalBytes = 100_000_000L,
            speedBytesPerSecond = 2_000_000L,
            stage = ModelDownloadNotificationStage.DOWNLOADING
        )
        val extras = foregroundInfo.notification.extras

        assertEquals(100, extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX))
        assertEquals(50, extras.getInt(NotificationCompat.EXTRA_PROGRESS))
        assertFalse(extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(ModelDownloadNotification.CHANNEL_ID, foregroundInfo.notification.channelId)
        assertNotNull(
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(ModelDownloadNotification.CHANNEL_ID)
        )
    }
}
