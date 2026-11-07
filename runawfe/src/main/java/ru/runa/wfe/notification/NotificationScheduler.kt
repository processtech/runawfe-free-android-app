package ru.runa.wfe.notification

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.ui.notification.DurationPreference
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object NotificationScheduler {
    const val NOTIFICATION_SERVICE_ID = 1
    const val FAST_NOTIFICATION_CHECK_WORK_NAME = "fastNotificationCheck"
    const val NORMAL_NOTIFICATION_CHECK_WORK_NAME = "normalNotificationCheck"
    private const val FLEX_INTERVAL: Long = 15 * 60 * 1_000
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresDeviceIdle(false)
        .build()
    private val notificationSchedulesScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pollingInterval: AtomicLong = AtomicLong(DurationPreference.DEFAULT)
    private val fastCheck: Boolean
        get() = pollingInterval.get() < (15 * 60)
    @Volatile
    private var schedulerJob: Job? = null

    fun start(context: Context) {
        val preferencesManager = PreferencesManager.getInstance(context)
        // DataStore uses Dispatchers.IO, this coroutine dispatcher won't change it
        stop(context)
        schedulerJob = notificationSchedulesScope.launch {
            val lastCheck = OffsetDateTime.parse(
                preferencesManager.getValue(
                    PreferencesManager.LAST_CHECK,
                    OffsetDateTime.now(ZoneOffset.UTC).toString()
                )
            )
                .withOffsetSameLocal(ZoneOffset.UTC)
            NotificationLogic.lastTasksCheck = lastCheck

            preferencesManager.getValueFlow(
                PreferencesManager.POLLING_INTERVAL,
                pollingInterval.get()
            ).collectLatest { value ->
                pollingInterval.set(value)
                /*
                The value is nonzero initially (checked in MainActivity)
                stopSelf() should be called only after service start
                */
                if (value == 0L) {
                    stop(context)
                } else {
                    val fastCheckMode = value < (15 * 60)
                    if (fastCheckMode) {
                        WorkManager.getInstance(context)
                            .cancelUniqueWork(NORMAL_NOTIFICATION_CHECK_WORK_NAME)
                    } else {
                        WorkManager.getInstance(context)
                            .cancelUniqueWork(FAST_NOTIFICATION_CHECK_WORK_NAME)
                    }
                    scheduleNext(context)
                }
            }
        }
        NotificationHelpers(context).createNotificationChannels()
    }

    private fun stopWorks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(
            if (fastCheck) FAST_NOTIFICATION_CHECK_WORK_NAME else NORMAL_NOTIFICATION_CHECK_WORK_NAME
        )
    }

    fun stop(context: Context) {
        schedulerJob?.cancel()
        schedulerJob = null
        CoroutineScope(Dispatchers.IO).launch {
            PreferencesManager.getInstance(context).setKey(
                PreferencesManager.LAST_CHECK,
                NotificationLogic.lastTasksCheck.toString()
            )
        }
        stopWorks(context)
        Log.i(this.javaClass.name, "Notification check work removed")
    }

    fun scheduleNext(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (fastCheck) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(workDataOf("fastCheck" to true))
                .setInitialDelay(pollingInterval.get(), TimeUnit.SECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                // This will turn ForegroundService mode. It works only with OneTimeWorkRequest
                // .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            workManager.enqueueUniqueWork(
                FAST_NOTIFICATION_CHECK_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        } else {
            val periodicRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                // Repeat interval
                pollingInterval.get(), TimeUnit.MILLISECONDS,
                // How long can the work take (will wait this time before start next repeat)
                FLEX_INTERVAL, TimeUnit.MILLISECONDS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                NORMAL_NOTIFICATION_CHECK_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }
    }
}