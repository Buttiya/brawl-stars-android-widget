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

        val result = runCatching {
            app.playerRepository.refreshWidgetData()
            val cache = app.playerRepository.getWidgetCache()
            WidgetAssetStore.saveProfileIcon(applicationContext, cache?.savedPlayerIconUrl)
            WidgetAssetStore.saveNextModeIcon(applicationContext, cache?.trackedCurrentModeIconUrl ?: cache?.trackedNextModeIconUrl)
            BrawlWidgetProvider.updateAll(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )

        BrawlWidgetProvider.restoreRefreshState(applicationContext)
        return result
    }
}
