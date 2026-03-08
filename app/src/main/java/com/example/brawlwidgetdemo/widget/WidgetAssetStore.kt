package com.example.brawlwidgetdemo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
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
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            file.delete()
        }
        return bitmap
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
            val bitmap = downloadBitmap(imageUrl) ?: return@runCatching
            val tempFile = File(context.filesDir, "$fileName.tmp")

            tempFile.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Не удалось сохранить иконку виджета"
                }
            }

            if (file.exists()) {
                file.delete()
            }

            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                return null
            }
            connection.inputStream.use { input ->
                val bytes = ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toByteArray()
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } finally {
            connection.disconnect()
        }
    }
}
