package com.example.brawlwidgetdemo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.URL

object WidgetAssetStore {
    private const val PROFILE_ICON_FILE = "widget_profile_icon.png"
    private const val NEXT_MODE_ICON_FILE = "widget_next_mode_icon.png"

    fun loadProfileIcon(context: Context): Bitmap? = loadBitmap(context, PROFILE_ICON_FILE)

    fun loadNextModeIcon(context: Context): Bitmap? = loadBitmap(context, NEXT_MODE_ICON_FILE)

    fun saveProfileIcon(context: Context, imageUrl: String?) {
        saveBitmap(context, PROFILE_ICON_FILE, imageUrl)
    }

    fun saveNextModeIcon(context: Context, imageUrl: String?) {
        saveBitmap(context, NEXT_MODE_ICON_FILE, imageUrl)
    }

    private fun loadBitmap(context: Context, fileName: String): Bitmap? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun saveBitmap(context: Context, fileName: String, imageUrl: String?) {
        val file = File(context.filesDir, fileName)
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
