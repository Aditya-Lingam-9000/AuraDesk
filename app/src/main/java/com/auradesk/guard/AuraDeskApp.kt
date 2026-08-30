package com.auradesk.guard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AuraDeskApp : Application() {

    companion object {
        const val CHANNEL_GUARD_SERVICE = "auradesk_guard_channel"
        const val CHANNEL_GUARD_ALERTS = "auradesk_alerts_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val guardChannel = NotificationChannel(
                CHANNEL_GUARD_SERVICE,
                "AuraDesk Guard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status when AuraDesk is guarding your deep work"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_GUARD_ALERTS,
                "AuraDesk Interruption Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for detected interruptions and summaries"
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(guardChannel)
            notificationManager?.createNotificationChannel(alertsChannel)
        }
    }
}
