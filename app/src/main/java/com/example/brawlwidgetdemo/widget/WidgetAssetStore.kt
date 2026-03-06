package com.example.brawlwidgetdemo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.URL

object WidgetAssetStore {
    private const val ICON_FILE = "widget_profile_icon.png"

    fun loadProfileIcon(context: Context): Bitmap? {
        val file = File(context.filesDir, ICON_FILE)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun saveProfileIcon(context: Context, imageUrl: String?) {
        val file = File(context.filesDir, ICON_FILE)
        if (imageUrl.isNullOrBlank()) {
            if (file.exists()) {
                file.delete()
            }
            return
        }

        runCatching {
            URL(imageUrl).openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
