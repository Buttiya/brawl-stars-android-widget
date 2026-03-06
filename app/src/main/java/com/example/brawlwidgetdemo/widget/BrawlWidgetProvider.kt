package com.example.brawlwidgetdemo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.brawlwidgetdemo.BrawlDemoApp
import com.example.brawlwidgetdemo.MainActivity
import com.example.brawlwidgetdemo.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.runBlocking

class BrawlWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val request = OneTimeWorkRequestBuilder<RefreshWidgetWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.brawlwidgetdemo.widget.ACTION_REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BrawlWidgetProvider::class.java))
            updateWidgets(context, ids)
        }

        private fun updateWidgets(context: Context, appWidgetIds: IntArray) {
            if (appWidgetIds.isEmpty()) return

            val app = context.applicationContext as BrawlDemoApp
            val cache = runBlocking { app.playerRepository.getWidgetCache() }
            val profileBitmap = WidgetAssetStore.loadProfileIcon(context)

            val manager = AppWidgetManager.getInstance(context)
            appWidgetIds.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_brawl)

                views.setTextViewText(R.id.widgetCurrentMap, cache?.soloCurrentMapName ?: "-" )
                views.setTextViewText(R.id.widgetNextMap, cache?.soloNextMapName ?: "TBD")
                views.setTextViewText(
                    R.id.widgetNextStart,
                    cache?.soloNextMapStartAt?.let {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                    } ?: "TBD"
                )

                val tag = cache?.savedPlayerTag
                views.setTextViewText(R.id.widgetPlayerTag, tag?.let { "#$it" } ?: "Select player")
                views.setTextViewText(R.id.widgetPlayerTrophies, "Trophies: ${cache?.savedPlayerTrophies ?: "-"}")

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
