package com.example.brawlwidgetdemo

import android.app.Application
import androidx.room.Room
import com.example.brawlwidgetdemo.data.db.AppDatabase
import com.example.brawlwidgetdemo.data.network.BrawlApiService
import com.example.brawlwidgetdemo.data.network.NetworkFactory
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

        val retrofit = NetworkFactory.createRetrofit()
        val api = retrofit.create(BrawlApiService::class.java)

        playerRepository = PlayerRepository(
            api = api,
            playerDao = db.playerDao(),
            snapshotDao = db.snapshotDao(),
            favoriteDao = db.favoriteDao(),
            widgetCacheDao = db.widgetCacheDao()
        )
    }
}
