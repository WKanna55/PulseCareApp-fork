package com.miempresa.pulsecare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.*

class UpdateRemindersService : Service() {

    private val repository: ReminderRepository = ReminderRepository()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val NOTIFICATION_CHANNEL_ID = "UpdateRemindersServiceChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startReminderUpdates()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Update Reminders Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Updating Reminders")
            .setContentText("This service is running in the background")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()
    }

    private fun startReminderUpdates() {
        serviceScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()

                repository.fetchAllReminders { reminders ->
                    reminders.forEach { reminder ->
                        //val remTimeMaxWait = reminder.reminderTime + 240000 // suma 4 min
                        if (reminder.reminderTime < currentTime && reminder.repeatDays.isNotEmpty()) {
                            val nextReminderTime = calculateNextReminderTime(reminder, currentTime)
                            if (nextReminderTime != null) {
                                repository.updateReminderTime(reminder, nextReminderTime) { success ->
                                    if (!success) {
                                        Log.e("UpdateRemindersService", "Failed to update reminder time")
                                    } else {
                                        Log.d("UpdateRemindersService", "Reminder time updated")
                                    }
                                }
                            }
                        }
                    }
                }

                delay(1000) // Esperar 1 minuto antes de la próxima verificación
            }
        }
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}