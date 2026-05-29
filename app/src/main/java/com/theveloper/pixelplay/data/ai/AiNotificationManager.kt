package com.theveloper.pixelplay.data.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.theveloper.pixelplay.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_PROGRESS = "ai_progress_channel"
        const val CHANNEL_COMPLETION = "ai_completion_channel"
        const val CHANNEL_ERROR = "ai_error_channel"
        const val CHANNEL_INFO = "ai_info_channel"
        const val PROGRESS_NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002
        const val ERROR_NOTIFICATION_ID = 1003
        const val INFO_NOTIFICATION_ID = 1004
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS, "AI Generation Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows ongoing AI task progress" }
            notificationManager.createNotificationChannel(progressChannel)

            val completionChannel = NotificationChannel(
                CHANNEL_COMPLETION, "AI Task Completed",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies when AI tasks finish successfully" }
            notificationManager.createNotificationChannel(completionChannel)

            val errorChannel = NotificationChannel(
                CHANNEL_ERROR, "AI Errors",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts for AI task failures" }
            notificationManager.createNotificationChannel(errorChannel)

            val infoChannel = NotificationChannel(
                CHANNEL_INFO, "AI Information",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Informational AI messages" }
            notificationManager.createNotificationChannel(infoChannel)
        }
    }

    fun showProgress(title: String, message: String, progress: Int, max: Int = 100, showIndeterminate: Boolean = false) {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(max, progress, showIndeterminate)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        notificationManager.notify(PROGRESS_NOTIFICATION_ID, builder.build())
    }

    fun showCompletion(title: String, message: String) {
        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETION)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, builder.build())
    }

    fun showError(title: String, message: String) {
        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)

        val builder = NotificationCompat.Builder(context, CHANNEL_ERROR)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        notificationManager.notify(ERROR_NOTIFICATION_ID, builder.build())
    }

    fun showInfo(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)

        notificationManager.notify(INFO_NOTIFICATION_ID, builder.build())
    }

    fun hideProgress() {
        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }
}
