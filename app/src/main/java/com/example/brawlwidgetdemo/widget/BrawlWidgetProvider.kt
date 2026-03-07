package com.example.brawlwidgetdemo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.brawlwidgetdemo.BrawlDemoApp
import com.example.brawlwidgetdemo.MainActivity
import com.example.brawlwidgetdemo.R
import kotlinx.coroutines.runBlocking

class BrawlWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runCatching {
            scheduleAutoRefresh(context)
            updateWidgets(context, appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        runCatching { scheduleAutoRefresh(context) }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        runCatching { cancelAutoRefresh(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            super.onReceive(context, intent)
            when (intent.action) {
                ACTION_REFRESH, ACTION_AUTO_REFRESH -> enqueueRefresh(context)
                Intent.ACTION_BOOT_COMPLETED -> {
                    scheduleAutoRefresh(context)
                    enqueueRefresh(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.brawlwidgetdemo.widget.ACTION_REFRESH"
        private const val ACTION_AUTO_REFRESH = "com.example.brawlwidgetdemo.widget.ACTION_AUTO_REFRESH"
        private const val AUTO_REFRESH_WORK_NAME = "widget_auto_refresh_work"
        private const val AUTO_REFRESH_REQUEST_CODE = 5001
        private const val AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

        private fun enqueueRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWidgetWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                AUTO_REFRESH_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        private fun autoRefreshIntent(context: Context): PendingIntent {
            val refreshIntent = Intent(context, BrawlWidgetProvider::class.java).apply {
                action = ACTION_AUTO_REFRESH
            }
            return PendingIntent.getBroadcast(
                context,
                AUTO_REFRESH_REQUEST_CODE,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun scheduleAutoRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = autoRefreshIntent(context)
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + AUTO_REFRESH_INTERVAL_MS,
                AUTO_REFRESH_INTERVAL_MS,
                pendingIntent
            )
        }

        private fun cancelAutoRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(autoRefreshIntent(context))
        }

        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, BrawlWidgetProvider::class.java))
                updateWidgets(context, ids)
            }
        }

        private fun updateWidgets(context: Context, appWidgetIds: IntArray) {
            if (appWidgetIds.isEmpty()) return

            val app = context.applicationContext as BrawlDemoApp
            val cache = runCatching { runBlocking { app.playerRepository.getWidgetCache() } }.getOrNull()
            val profileBitmap = WidgetAssetStore.loadProfileIcon(context)
            val modeBitmap = WidgetAssetStore.loadNextModeIcon(context)

            val manager = AppWidgetManager.getInstance(context)
            appWidgetIds.forEach { id ->
                runCatching {
                    val views = RemoteViews(context.packageName, R.layout.widget_brawl)

                    views.setTextViewText(R.id.widgetCurrentMap, cache?.soloCurrentMapName ?: "-")
                    views.setTextViewText(R.id.widgetNextMap, cache?.soloNextMapName ?: "TBD")

                    if (modeBitmap != null) {
                        views.setViewVisibility(R.id.widgetModeIcon, View.VISIBLE)
                        views.setImageViewBitmap(R.id.widgetModeIcon, modeBitmap)
                    } else {
                        views.setViewVisibility(R.id.widgetModeIcon, View.INVISIBLE)
                    }

                    val playerName = cache?.savedPlayerName
                    val fallbackTag = cache?.savedPlayerTag?.let { "#$it" }
                    views.setTextViewText(R.id.widgetPlayerTag, playerName ?: fallbackTag ?: "Select player")
                    views.setTextViewText(R.id.widgetPlayerTrophies, "Trophies: ${cache?.savedPlayerTrophies ?: "-"}")
                    views.setTextViewText(R.id.widgetPlayerExp, "EXP: ${cache?.savedPlayerExpLevel ?: "-"}")

                    if (profileBitmap != null) {
                        views.setImageViewBitmap(R.id.widgetProfileIcon, profileBitmap)
                    } else {
                        views.setImageViewResource(R.id.widgetProfileIcon, android.R.drawable.sym_def_app_icon)
                    }

                    val refreshIntent = Intent(context, BrawlWidgetProvider::class.java).apply {
                        action = ACTION_REFRESH
                    }
                    val refreshPendingIntent = PendingIntent.getBroadcast(
                        context,
                        id,
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widgetRefreshButton, refreshPendingIntent)

                    val openIntent = Intent(context, MainActivity::class.java)
                    val openPendingIntent = PendingIntent.getActivity(
                        context,
                        id + 1000,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widgetPlayerSection, openPendingIntent)

                    manager.updateAppWidget(id, views)
                }
            }
        }
    }
}
