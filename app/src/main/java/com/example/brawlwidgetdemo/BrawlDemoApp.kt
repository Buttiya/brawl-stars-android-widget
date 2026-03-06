package com.example.brawlwidgetdemo

import android.app.Application
import androidx.room.Room
import com.example.brawlwidgetdemo.data.db.AppDatabase
import com.example.brawlwidgetdemo.data.network.BrawlApiService
import com.example.brawlwidgetdemo.data.network.NetworkFactory
import com.example.brawlwidgetdemo.data.network.OfficialBrawlStarsService
import com.example.brawlwidgetdemo.data.repo.PlayerRepository

class BrawlDemoApp : Application() {
    lateinit var playerRepository: PlayerRepository
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

        val primaryApi = NetworkFactory.createPrimaryRetrofit().create(BrawlApiService::class.java)
        val secondaryApi = NetworkFactory.createSecondaryRetrofit().create(BrawlApiService::class.java)

        val officialApi: OfficialBrawlStarsService? =
            if (BuildConfig.BRAWL_STARS_API_TOKEN.isBlank()) {
                null
            } else {
                NetworkFactory.createOfficialRetrofit(BuildConfig.BRAWL_STARS_API_TOKEN)
                    .create(OfficialBrawlStarsService::class.java)
            }

        playerRepository = PlayerRepository(
            api = primaryApi,
            secondaryApi = secondaryApi,
            officialApi = officialApi,
            playerDao = db.playerDao(),
            snapshotDao = db.snapshotDao(),
            favoriteDao = db.favoriteDao(),
            widgetCacheDao = db.widgetCacheDao()
        )
    }
}
