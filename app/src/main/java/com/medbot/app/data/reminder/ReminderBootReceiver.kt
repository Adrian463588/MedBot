package com.medbot.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medbot.app.data.local.database.MedBotDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = MedBotDatabase.getDatabase(context)
                    val reminderEntities = db.healthToolsDao().getReminders().firstOrNull() ?: emptyList()
                    val scheduler = ReminderScheduler(context)
                    for (entity in reminderEntities) {
                        if (entity.isEnabled) {
                            val days = if (entity.daysOfWeekCsv.isBlank()) listOf(1, 2, 3, 4, 5, 6, 7) else {
                                entity.daysOfWeekCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
                            }
                            val type = try {
                                com.medbot.app.domain.model.ReminderType.valueOf(entity.type)
                            } catch (_: Exception) {
                                com.medbot.app.domain.model.ReminderType.MEDICATION
                            }
                            val mode = try {
                                com.medbot.app.domain.model.ReminderNotificationMode.valueOf(entity.notificationMode)
                            } catch (_: Exception) {
                                com.medbot.app.domain.model.ReminderNotificationMode.SOUND_AND_VIBRATE
                            }
                            scheduler.scheduleReminder(
                                com.medbot.app.domain.model.Reminder(
                                    id = entity.id,
                                    type = type,
                                    title = entity.title,
                                    timeHour = entity.timeHour,
                                    timeMinute = entity.timeMinute,
                                    daysOfWeek = days,
                                    isEnabled = entity.isEnabled,
                                    notificationMode = mode,
                                    notes = entity.notes
                                )
                            )
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
