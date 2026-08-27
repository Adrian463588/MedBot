package com.medbot.app.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.medbot.app.MainActivity
import com.medbot.app.R
import com.medbot.app.domain.model.Reminder
import com.medbot.app.domain.model.ReminderNotificationMode
import com.medbot.app.domain.model.ReminderType

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_TRIGGER_REMINDER) return

        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Pengingat Kesehatan"
        val typeStr = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TYPE) ?: ReminderType.MEDICATION.name
        val modeStr = intent.getStringExtra(ReminderScheduler.EXTRA_NOTIFICATION_MODE) ?: ReminderNotificationMode.SOUND_AND_VIBRATE.name
        val hour = intent.getIntExtra(ReminderScheduler.EXTRA_TIME_HOUR, 8)
        val minute = intent.getIntExtra(ReminderScheduler.EXTRA_TIME_MINUTE, 0)

        val mode = try {
            ReminderNotificationMode.valueOf(modeStr)
        } catch (_: Exception) {
            ReminderNotificationMode.SOUND_AND_VIBRATE
        }

        val type = try {
            ReminderType.valueOf(typeStr)
        } catch (_: Exception) {
            ReminderType.MEDICATION
        }

        showNotification(context, reminderId, title, type, mode)

        // Reschedule next occurrence for repeating daily reminder
        val scheduler = ReminderScheduler(context)
        scheduler.scheduleReminder(
            Reminder(
                id = reminderId,
                type = type,
                title = title,
                timeHour = hour,
                timeMinute = minute,
                notificationMode = mode
            )
        )
    }

    private fun showNotification(
        context: Context,
        reminderId: String,
        title: String,
        type: ReminderType,
        mode: ReminderNotificationMode
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channelId = when (mode) {
            ReminderNotificationMode.SOUND_AND_VIBRATE -> CHANNEL_ID_SOUND
            ReminderNotificationMode.VIBRATE_ONLY -> CHANNEL_ID_VIBRATE
            ReminderNotificationMode.SILENT -> CHANNEL_ID_SILENT
        }

        createNotificationChannels(context, notificationManager)

        // Trigger physical haptic vibration if vibrate or sound+vibrate mode
        if (mode == ReminderNotificationMode.VIBRATE_ONLY || mode == ReminderNotificationMode.SOUND_AND_VIBRATE) {
            triggerVibration(context)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_tools", true)
            putExtra("tools_tab", 3) // Reminders tab
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val headerTitle = when (type) {
            ReminderType.MEDICATION -> "⏰ Waktunya Minum Obat"
            ReminderType.WATER -> "💧 Waktunya Minum Air"
            ReminderType.VITALS_CHECK -> "🩺 Waktunya Pemeriksaan Kesehatan"
            ReminderType.EXERCISE -> "🏃 Waktunya Aktivitas Fisik"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(headerTitle)
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(
                if (mode == ReminderNotificationMode.SILENT) NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                when (mode) {
                    ReminderNotificationMode.SOUND_AND_VIBRATE -> {
                        setDefaults(NotificationCompat.DEFAULT_ALL)
                    }
                    ReminderNotificationMode.VIBRATE_ONLY -> {
                        setSound(null)
                        setVibrate(VIBRATION_PATTERN)
                    }
                    ReminderNotificationMode.SILENT -> {
                        setSound(null)
                        setVibrate(null)
                    }
                }
            }
            .build()

        notificationManager.notify(reminderId.hashCode(), notification)
    }

    private fun triggerVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(VIBRATION_PATTERN, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(VIBRATION_PATTERN, -1)
                }
            }
        } catch (_: Exception) {
            // Ignore vibration exception if permission restricted
        }
    }

    private fun createNotificationChannels(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            // 1. Sound + Vibrate Channel
            val soundChannel = NotificationChannel(
                CHANNEL_ID_SOUND,
                "Pengingat Kesehatan (Suara & Getar)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengingat dengan nada dering dan getaran"
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(soundUri, audioAttributes)
            }

            // 2. Vibrate Only Channel
            val vibrateChannel = NotificationChannel(
                CHANNEL_ID_VIBRATE,
                "Pengingat Kesehatan (Hanya Getar)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengingat hanya dengan getaran tanpa suara"
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(null, null)
            }

            // 3. Silent Channel
            val silentChannel = NotificationChannel(
                CHANNEL_ID_SILENT,
                "Pengingat Kesehatan (Hening / Tanpa Suara)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi visual tanpa suara maupun getaran"
                enableVibration(false)
                setSound(null, null)
            }

            manager.createNotificationChannels(listOf(soundChannel, vibrateChannel, silentChannel))
        }
    }

    companion object {
        const val CHANNEL_ID_SOUND = "medbot_reminders_sound_channel"
        const val CHANNEL_ID_VIBRATE = "medbot_reminders_vibrate_channel"
        const val CHANNEL_ID_SILENT = "medbot_reminders_silent_channel"
        val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 400)
    }
}
