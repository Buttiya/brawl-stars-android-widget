package com.example.brawlwidgetdemo.data.repo

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionTokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun read(): String? = prefs.getString(KEY_SESSION_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun write(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) {
                remove(KEY_SESSION_TOKEN)
            } else {
                putString(KEY_SESSION_TOKEN, token)
            }
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "secure_session_store"
        const val KEY_SESSION_TOKEN = "session_token"
    }
}
