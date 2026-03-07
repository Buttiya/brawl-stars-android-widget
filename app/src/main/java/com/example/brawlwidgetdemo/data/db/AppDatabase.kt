package com.example.brawlwidgetdemo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlayerEntity::class,
        PlayerSnapshotEntity::class,
        FavoriteEntity::class,
        WidgetCacheEntity::class,
        UserAccountEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun widgetCacheDao(): WidgetCacheDao
    abstract fun userAccountDao(): UserAccountDao
}

