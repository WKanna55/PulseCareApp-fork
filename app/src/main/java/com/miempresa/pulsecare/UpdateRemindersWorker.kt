package com.miempresa.pulsecare

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.*

class UpdateRemindersWorker(context: Context, params: WorkerParameters): Worker(context, params) {

    private val repository: ReminderRepository = ReminderRepository()

    override fun doWork(): Result {
        val currentTime = System.currentTimeMillis()

        repository.fetchAllReminders { reminders ->
            reminders.forEach { reminder ->
                //var remTimeMaxWait = reminder.reminderTime + 240000 // suma 4 min
                if (reminder.reminderTime < currentTime && reminder.repeatDays.isNotEmpty()) {
                    val nextReminderTime = calculateNextReminderTime(reminder, currentTime)
                    if (nextReminderTime != null) {
                        repository.updateReminderTime(reminder, nextReminderTime) { success ->
                            if (!success) {
                                Log.e("UpdateRemindersWorker", "Failed to update reminder time")
                            } else {
                                Log.d("UpdateRemindersWorker", "Reminder time updated")
                            }
                        }
                    }
                }
            }
        }

        return Result.success()
    }

    private fun calculateNextReminderTime(reminder: Reminder, currentTime: Long): Long? {
        val calendar = Calendar.getInstance().apply { timeInMillis = reminder.reminderTime }
        val today = calendar.get(Calendar.DAY_OF_WEEK)
        for (i in 1..7) {
            val nextDay = (today + i) % 7
            if (reminder.repeatDays.contains(nextDay)) {
                calendar.add(Calendar.DAY_OF_YEAR, i)
                return calendar.timeInMillis
            }
        }
        return null
    }
}