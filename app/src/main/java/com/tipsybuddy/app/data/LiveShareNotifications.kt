package com.tipsybuddy.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tipsybuddy.app.MainActivity
import com.tipsybuddy.app.R

const val CHANNEL_WAVE = "wave_channel"
const val WAVE_NOTIFICATION_ID = 4041

fun showWaveNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_WAVE,
            "Friend Waves",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a friend waves at you from the live tracking page"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val contentIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        WAVE_NOTIFICATION_ID,
        contentIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_WAVE)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("👋 A friend waved at you!")
        .setContentText("Someone tracking your night out sent a check-in wave.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    try {
        NotificationManagerCompat.from(context).notify(WAVE_NOTIFICATION_ID, notification)
    } catch (e: SecurityException) {
        // POST_NOTIFICATIONS not granted; silently skip.
    }
}
