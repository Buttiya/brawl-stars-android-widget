package com.example.brawlwidgetdemo.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.brawlwidgetdemo.BrawlDemoApp

class RefreshWidgetWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BrawlDemoApp

        return runCatching {
            app.playerRepository.refreshWidgetData()
            val iconUrl = app.playerRepository.getWidgetCache()?.savedPlayerIconUrl
            WidgetAssetStore.saveProfileIcon(applicationContext, iconUrl)
            BrawlWidgetProvider.updateAll(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
