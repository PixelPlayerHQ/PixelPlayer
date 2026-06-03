package com.theveloper.pixelplay.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File

import androidx.work.ForegroundInfo
import android.content.pm.ServiceInfo

class DownloadNotificationHelper(private val context: Context) {
    // ...
    fun getForegroundInfo(id: Int, title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOngoing(true)
            .build()
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }
    companion object {
        const val CHANNEL_ID = "download_channel"
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    fun showProgress(id: Int, title: String, progress: Int, max: Int = 100) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, false)
            .setOngoing(true)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun showComplete(id: Int, title: String, file: File) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Saved to ${file.absolutePath}")
            .setProgress(0, 0, false)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun showFailed(id: Int, title: String, message: String?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message ?: "Download failed")
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
