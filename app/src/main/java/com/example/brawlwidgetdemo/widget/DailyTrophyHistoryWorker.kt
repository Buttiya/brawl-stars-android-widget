package com.example.brawlwidgetdemo.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.brawlwidgetdemo.BrawlDemoApp
import java.util.concurrent.TimeUnit

class DailyTrophyHistoryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BrawlDemoApp
        return runCatching {
            val tag = app.authRepository.getCurrentAccount()?.linkedPlayerTag
            if (!tag.isNullOrBlank()) {
                app.playerRepository.refreshPlayer(tag).getOrThrow()
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_trophy_history_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyTrophyHistoryWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
