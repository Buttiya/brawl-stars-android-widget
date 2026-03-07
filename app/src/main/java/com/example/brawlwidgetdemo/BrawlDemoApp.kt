package com.example.brawlwidgetdemo

import android.app.Application
import androidx.room.Room
import com.example.brawlwidgetdemo.data.db.AppDatabase
import com.example.brawlwidgetdemo.data.network.NetworkFactory
import com.example.brawlwidgetdemo.data.network.OfficialBrawlStarsService
import com.example.brawlwidgetdemo.data.repo.AuthRepository
import com.example.brawlwidgetdemo.data.repo.PlayerRepository

class BrawlDemoApp : Application() {
    lateinit var playerRepository: PlayerRepository
        private set

    lateinit var authRepository: AuthRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "brawl_demo.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val officialApi: OfficialBrawlStarsService? =
            if (BuildConfig.BRAWL_STARS_API_TOKEN.isBlank()) {
                null
            } else {
                NetworkFactory.createOfficialRetrofit(BuildConfig.BRAWL_STARS_API_TOKEN)
                    .create(OfficialBrawlStarsService::class.java)
            }

        playerRepository = PlayerRepository(
            officialApi = officialApi,
            playerDao = db.playerDao(),
            snapshotDao = db.snapshotDao(),
            favoriteDao = db.favoriteDao(),
            widgetCacheDao = db.widgetCacheDao()
        )

        authRepository = AuthRepository(db.userAccountDao())
    }
}


