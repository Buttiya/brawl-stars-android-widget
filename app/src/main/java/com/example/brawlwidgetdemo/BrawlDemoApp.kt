package com.example.brawlwidgetdemo

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.brawlwidgetdemo.data.db.AppDatabase
import com.example.brawlwidgetdemo.data.network.NetworkFactory
import com.example.brawlwidgetdemo.data.network.ProxyApiService
import com.example.brawlwidgetdemo.data.repo.AuthRepository
import com.example.brawlwidgetdemo.data.repo.PlayerRepository
import com.example.brawlwidgetdemo.data.repo.SessionTokenStore
import com.example.brawlwidgetdemo.widget.DailyTrophyHistoryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BrawlDemoApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: AppDatabase

    lateinit var playerRepository: PlayerRepository
        private set

    lateinit var authRepository: AuthRepository
        private set

    override fun onCreate() {
        super.onCreate()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "brawl_demo.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val savedProxyBaseUrl = getSavedProxyBaseUrl()
        if (savedProxyBaseUrl.isNotBlank()) {
            initializeRepositories(savedProxyBaseUrl)
        }
    }

    fun getSavedProxyBaseUrl(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PROXY_BASE_URL, defaultProxyBaseUrl()).orEmpty()
        if (saved == LEGACY_DEFAULT_PROXY_BASE_URL) {
            prefs.edit().remove(KEY_PROXY_BASE_URL).apply()
            return ""
        }
        return saved.takeIf { it.isNotBlank() } ?: defaultProxyBaseUrl()
    }

    fun saveAndApplyProxyBaseUrl(rawUrl: String) {
        val normalized = normalizeProxyBaseUrl(rawUrl)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROXY_BASE_URL, normalized).apply()
        initializeRepositories(normalized)
    }

    fun isDarkThemeEnabled(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_THEME, false)
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    private fun initializeRepositories(proxyBaseUrl: String) {
        val proxyApi = NetworkFactory.createProxyRetrofit(
            baseUrl = proxyBaseUrl,
            enableLogging = BuildConfig.DEBUG
        )
            .create(ProxyApiService::class.java)

        playerRepository = PlayerRepository(
            proxyApi = proxyApi,
            playerDao = db.playerDao(),
            snapshotDao = db.snapshotDao(),
            dailyTrophyHistoryDao = db.dailyTrophyHistoryDao(),
            favoriteDao = db.favoriteDao(),
            widgetCacheDao = db.widgetCacheDao()
        )

        authRepository = AuthRepository(
            proxyApi = proxyApi,
            userAccountDao = db.userAccountDao(),
            sessionTokenStore = SessionTokenStore(applicationContext)
        )

        appScope.launch {
            authRepository.syncSession()
        }

        DailyTrophyHistoryWorker.schedule(applicationContext)
    }

    private fun normalizeProxyBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private companion object {
        const val PREFS_NAME = "app_runtime_config"
        const val KEY_PROXY_BASE_URL = "proxy_base_url"
        const val KEY_DARK_THEME = "dark_theme"
        const val LEGACY_DEFAULT_PROXY_BASE_URL = "http://192.168.0.111:8787/"
    }

    private fun defaultProxyBaseUrl(): String {
        return if (BuildConfig.DEBUG) "" else BuildConfig.BRAWL_PROXY_BASE_URL
    }
}


