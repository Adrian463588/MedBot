package com.medbot.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.medbot.app.MainActivity
import com.medbot.app.R
import kotlin.math.roundToInt

/** Stages shown by the durable model-transfer foreground notification. */
internal enum class ModelDownloadNotificationStage {
    DOWNLOADING,
    VERIFYING
}

/**
 * Builds the notification used by the long-running model worker.
 *
 * Progress is derived only from bytes reported by the worker. The notification
 * never invents an ETA or a completion state, and it contains no credentials.
 */
internal object ModelDownloadNotification {
    const val CHANNEL_ID = "medbot_model_downloads"

    private const val NOTIFICATION_ID_BASE = 20_000
    private const val MEGABYTE = 1_000_000.0

    fun createForegroundInfo(
        context: Context,
        modelId: String,
        displayName: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        stage: ModelDownloadNotificationStage
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = buildNotification(
            context = context,
            modelId = modelId,
            displayName = displayName,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            speedBytesPerSecond = speedBytesPerSecond,
            stage = stage
        )
        return ForegroundInfo(
            notificationId(modelId),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun buildNotification(
        context: Context,
        modelId: String,
        displayName: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        stage: ModelDownloadNotificationStage
    ): Notification {
        val percent = progressPercent(bytesDownloaded, totalBytes)
        val contentText = when (stage) {
            ModelDownloadNotificationStage.DOWNLOADING -> context.getString(
                R.string.model_download_notification_progress,
                percent,
                bytesToMegabytes(bytesDownloaded),
                bytesToMegabytes(totalBytes),
                bytesToMegabytes(speedBytesPerSecond)
            )
            ModelDownloadNotificationStage.VERIFYING -> context.getString(
                R.string.model_download_notification_verifying
            )
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(modelId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_MODEL_MANAGER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                context.getString(
                    if (stage == ModelDownloadNotificationStage.DOWNLOADING) {
                        R.string.model_download_notification_title
                    } else {
                        R.string.model_download_notification_verifying_title
                    },
                    displayName
                )
            )
            .setContentText(contentText)
            .setSubText(context.getString(R.string.app_name))
            .setContentIntent(contentIntent)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.model_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.model_download_notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun notificationId(modelId: String): Int =
        NOTIFICATION_ID_BASE + (modelId.hashCode() and 0x3FFF)

    private fun progressPercent(bytesDownloaded: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        return ((bytesDownloaded.coerceIn(0L, totalBytes).toDouble() / totalBytes) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun bytesToMegabytes(bytes: Long): Double =
        bytes.coerceAtLeast(0L) / MEGABYTE
}
