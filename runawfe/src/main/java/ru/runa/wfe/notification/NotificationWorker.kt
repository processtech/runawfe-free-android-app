package ru.runa.wfe.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.runa.wfe.R
import ru.runa.wfe.notification.NotificationHelpers.NotificationType
import java.io.IOException

class NotificationWorker(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelType = NotificationType.DEFAULT
        val notificationManager =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val serviceChannel = notificationManager.getNotificationChannel(channelType.channelId)
            ?: NotificationChannel(
                channelType.channelId,
                context.resources.getString(channelType.titleResId),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        serviceChannel.setShowBadge(false)
        notificationManager.createNotificationChannel(serviceChannel)
        val serviceStartNotification = NotificationCompat.Builder(
            context,
            NotificationType.DEFAULT.channelId
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.resources.getString(R.string.notifications_service_title))
            .setContentText(context.resources.getString(R.string.notifications_service_message))
            .addAction(
                android.R.drawable.ic_delete,
                context.resources.getString(R.string.stop_notification_service),
                WorkManager.getInstance(context).createCancelPendingIntent(id)
            )
            .build()

        return ForegroundInfo(
            NotificationScheduler.NOTIFICATION_SERVICE_ID,
            serviceStartNotification
        )
    }

    override suspend fun doWork(): Result {
        try {
            val fastCheckEnabled: Boolean = inputData.getBoolean("fastCheck", false)
            withContext(Dispatchers.IO) {
                // When long work execution is needed, use the line below
                // setForeground(getForegroundInfo())
                // The line below is deprecated since Android 12, use setExpedited() in NotificationScheduler
                // setForegroundAsync(getForegroundInfo())
                val notificationCheck = launch {
                    NotificationLogic(
                        context,
                        NotificationHelpers(context)
                    ).collectNotificationData()
                }
                notificationCheck.join()
                if (fastCheckEnabled) {
                    NotificationScheduler.scheduleNext(context)
                }
            }
        } catch (ex: IOException) {
            Log.i(this.javaClass.name, ex.message.toString())
            return Result.retry()
        } catch (ex: Exception) {
            Log.e(this.javaClass.name, ex.message.toString())
            return Result.failure()
        }
        return Result.success()
    }
}